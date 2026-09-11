package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食堂管理mcp工具集 - 来自platform平台
 */
@Service
public class PlatformDiningHallMcp {

    private static final String TOOL_NAME_PREFIX = "canteen:";
    private static final String TOOL_DESC_PREFIX = "食堂管理服务:";

    // 查询每周食谱接口URL
    public static final String WEEKLY_RECIPE_URL = "/calldevice/dininghall/getWeeklyRecipe";
    // 接口必传参数:调用方标识(生产者编码)
    public static final String PRODUCER_CODE = "zhiye";
    // 接口必传参数:协议版本号
    public static final String PROTOCOL_VERSION = "V1.0";

    // 查询菜品热度排行接口URL
    public static final String DISH_POPULARITY_URL = "/canteen/getDishPopularity";
    // 查询就餐人数统计接口URL
    public static final String DINING_COUNT_URL = "/canteen/diningCount";

    // 周菜谱缓存：key = 本周一日期（yyyy-MM-dd），value = 整形后的整周菜谱数据
    // 周菜谱一周内基本不变，重复查询直接返回缓存、不再打平台（平台每次返回整周数据，很慢又很大）
    // key 带"本周一"，周切换时自动换 key；写入时只保留当周数据，旧周缓存即时清理
    private static final Map<String, Object> WEEK_MENU_CACHE = new ConcurrentHashMap<>();

    /**
     * 返回本周一的日期字符串，作为周菜谱缓存的 key
     */
    private static String getWeekKey() {
        LocalDate today = LocalDate.now();
        // DayOfWeek.getValue(): 周一=1 ... 周日=7，本周一 = 今天 - (周几 - 1)
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        return monday.toString();
    }

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getWeekMenu",
            description = TOOL_DESC_PREFIX + """
            查询食堂每周食谱,返回当周每天的食谱数组,每个元素结构如下:
            - day: 星期几,如"周一"
            - breakfast / lunch / dinner: 早/中/晚餐菜品数组,每道菜包含:
              - dishName: 菜名
              - price: 价格
            请根据用户问题(如"今天中午吃什么"/"本周菜谱")从返回中提取对应信息回答。
            """
    )
    public String getWeeklyRecipe() {
        // 周菜谱按"本周一"缓存：重复查询直接返回缓存，不打平台
        String weekKey = getWeekKey();
        Object cached = WEEK_MENU_CACHE.get(weekKey);
        if (cached != null) {
            return JSON.toJSONString(cached);
        }

        // 首次查询该周：POST 平台拉整周菜谱
        // 该接口是 POST 方式，producerCode / protocolVersion 要放请求体里，不能拼 query
        String url = platformApiCallServiceImpl.getBaseUrl() + WEEKLY_RECIPE_URL;
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", PRODUCER_CODE);
        body.put("protocolVersion", PROTOCOL_VERSION);
        Object raw = platformApiCallServiceImpl.doPostCall(url, body);

        // 整形：只保留 day / 餐次 / dishName / price，丢掉 image/elementJson 等长字段，
        // 减小传输体积，也减少 Python 端喂给 LLM 的 token，然后缓存整形后的数据
        List<Map<String, Object>> shaped = shapeWeeklyRecipe(raw);
        // 只有拿到真实数据才缓存，避免把空/异常结果一直缓存住
        if (!shaped.isEmpty()) {
            // 只保存当周：写入前清掉其它周的旧缓存，防止 map 无限增长
            WEEK_MENU_CACHE.keySet().removeIf(key -> !weekKey.equals(key));
            WEEK_MENU_CACHE.put(weekKey, shaped);
        }
        return JSON.toJSONString(shaped);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getDishPopularity",
            description = TOOL_DESC_PREFIX + """
            查询菜品热度排行,返回热度最高的前5道菜,每项结构如下:
            - rank: 排名
            - name: 菜名
            - price: 价格
            - soldCount: 销量
            - likes: 点赞数
            - positiveReview: 好评数
            支持按时间范围查询,请根据用户问题(如"哪些菜最受欢迎"/"本月菜品热度排行")回答。
            """
    )
    public String getDishPopularity(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        // GET 接口,直接拼 URL 调用平台;如传了时间参数则拼接到 query string
        String url = buildUrlWithTimeParams(platformApiCallServiceImpl.getBaseUrl() + DISH_POPULARITY_URL, startTime, endTime);
        Object raw = platformApiCallServiceImpl.doGetCall(url);
        // 整形:只保留 rank/name/price/soldCount/likes/positiveReview,并截取前5
        List<Map<String, Object>> shaped = shapeDishPopularity(raw);
        return JSON.toJSONString(shaped);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getDiningCount",
            description = TOOL_DESC_PREFIX + """
            查询就餐人数统计,返回结构如下:
            - category: 分类(时间段,如早餐/午餐/晚餐 或具体时间点)数组
            - values: 与 category 对应的就餐人数数组
            支持按时间范围和餐次(早餐/午餐/晚餐)查询,请根据用户问题(如"今天就餐人数"/"用餐高峰是什么时候")回答。
            """
    )
    public String getDiningCount(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime,
            @McpToolParam(description = "餐次:早餐/午餐/晚餐,可不传") String meal
    ) {
        // GET 接口,直接拼 URL 调用平台,如传了时间参数或餐次则拼接到 query string
        String url = buildUrlWithTimeAndMealParams(platformApiCallServiceImpl.getBaseUrl() + DINING_COUNT_URL, startTime, endTime, meal);
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    /**
     * 把平台返回的整周菜谱压成紧凑结构再缓存/返回：
     *   data: [ { day, breakfast: [{dishName, price}], lunch: [...], dinner: [...] }, ... ]
     */
    private static List<Map<String, Object>> shapeWeeklyRecipe(Object raw) {
        List<Map<String, Object>> shaped = new ArrayList<>();
        if (!(raw instanceof List)) {
            return shaped;
        }
        for (Object dayObj : (List<?>) raw) {
            if (!(dayObj instanceof Map)) {
                continue;
            }
            Map<?, ?> dayMap = (Map<?, ?>) dayObj;
            Map<String, Object> shapedDay = new LinkedHashMap<>();
            shapedDay.put("day", dayMap.get("day"));
            shapedDay.put("breakfast", shapeMeal(dayMap.get("breakfast")));
            shapedDay.put("lunch", shapeMeal(dayMap.get("lunch")));
            shapedDay.put("dinner", shapeMeal(dayMap.get("dinner")));
            shaped.add(shapedDay);
        }
        return shaped;
    }

    /**
     * 一餐的菜品数组 -> 只保留 {dishName, price}
     */
    private static List<Map<String, Object>> shapeMeal(Object meal) {
        List<Map<String, Object>> shaped = new ArrayList<>();
        if (!(meal instanceof List)) {
            return shaped;
        }
        for (Object dishObj : (List<?>) meal) {
            if (!(dishObj instanceof Map)) {
                continue;
            }
            Map<?, ?> dishMap = (Map<?, ?>) dishObj;
            Map<String, Object> shapedDish = new LinkedHashMap<>();
            shapedDish.put("dishName", dishMap.get("dishName"));
            shapedDish.put("price", dishMap.get("price"));
            shaped.add(shapedDish);
        }
        return shaped;
    }

    /**
     * 把平台返回的菜品热度数据压成紧凑结构并只保留前5:
     *   data: [ { rank, name, price, soldCount, likes, positiveReview }, ... ] (最多5条)
     */
    private static List<Map<String, Object>> shapeDishPopularity(Object raw) {
        List<Map<String, Object>> shaped = new ArrayList<>();
        if (!(raw instanceof List)) {
            return shaped;
        }
        List<?> list = (List<?>) raw;
        int size = Math.min(list.size(), 5); // 只取热度前5
        for (int i = 0; i < size; i++) {
            Object dishObj = list.get(i);
            if (!(dishObj instanceof Map)) {
                continue;
            }
            Map<?, ?> dishMap = (Map<?, ?>) dishObj;
            Map<String, Object> shapedDish = new LinkedHashMap<>();
            // rank 优先取平台返回的排名，没有则用顺序下标(从1开始)
            Object rank = getValue(dishMap, "rank", "ranking", "sort");
            shapedDish.put("rank", rank == null ? i + 1 : toInt(rank));
            shapedDish.put("name", getStr(getValue(dishMap, "name", "dishName"), ""));
            shapedDish.put("price", toNum(getValue(dishMap, "price")));
            shapedDish.put("soldCount", toInt(getValue(dishMap, "soldCount", "saleCount", "salesCount")));
            shapedDish.put("likes", toInt(getValue(dishMap, "likes", "likeCount", "likeNum")));
            shapedDish.put("positiveReview", toInt(getValue(dishMap, "positiveReview", "positiveRate", "goodReview", "praiseCount")));
            shaped.add(shapedDish);
        }
        return shaped;
    }

    /**
     * 按候选 key 依次取值,返回第一个非空 key 的值;全部为空则返回 null
     */
    private static Object getValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * 转 int,为 null 或解析失败时返回 0
     */
    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 转数字(保留原值类型),为 null 或解析失败时返回 0
     */
    private static Number toNum(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return (Number) v;
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 取字符串,为 null 时返回默认值
     */
    private static String getStr(Object v, String defaultVal) {
        return v == null ? defaultVal : v.toString();
    }

    /**
     * 给 GET URL 追加可选的时间参数(startTime/endTime)
     */
    private static String buildUrlWithTimeParams(String baseUrl, String startTime, String endTime) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = !baseUrl.contains("?");
        if (startTime != null && !startTime.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append("startTime=").append(encode(startTime));
            first = false;
        }
        if (endTime != null && !endTime.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append("endTime=").append(encode(endTime));
        }
        return sb.toString();
    }

    /**
     * 给 GET URL 追加可选的时间参数(startTime/endTime)和餐次参数(meal)
     */
    private static String buildUrlWithTimeAndMealParams(String baseUrl, String startTime, String endTime, String meal) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = !baseUrl.contains("?");
        if (startTime != null && !startTime.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append("startTime=").append(encode(startTime));
            first = false;
        }
        if (endTime != null && !endTime.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append("endTime=").append(encode(endTime));
            first = false;
        }
        if (meal != null && !meal.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append("meal=").append(encode(meal));
        }
        return sb.toString();
    }

    /**
     * URL 参数编码,避免中文或特殊字符破坏 query string
     */
    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
