package com.hx.mcpsidecar.service.mcp.scene;

import com.hx.mcpsidecar.scene.SceneActions;
import com.hx.mcpsidecar.scene.SceneDispatcher;
import com.hx.mcpsidecar.scene.SceneOutcome;
import com.hx.mcpsidecar.scene.SceneProperties;
import com.hx.mcpsidecar.scene.SceneSession;
import com.hx.mcpsidecar.scene.SceneSessionRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景控制类 MCP 工具（方案 §四，19 个动作工具 + 1 个查询工具）。
 *
 * <p><b>工具内部不实现任何 UE 逻辑</b>，那是前端 {@code SceneExecutor} 的活。这里只做：
 * 拼参数 → {@link SceneDispatcher#dispatch} 下发并等回执 → 把结果翻成给模型看的文本。</p>
 *
 * <p><b>不要为了"铺场"额外调工具。</b>前端执行器自己会在每条 {@code FocusTo} 前补一条
 * {@code ViewActor} 把目标显出来，重发是幂等的。想让点位可见，调对应的组合动作或
 * {@code scene_focus_actor} 就够了 —— 再叠一次 {@code scene_set_actor_visible}
 * 只会多发一条冗余帧。</p>
 *
 * <p><b>sessionId 从 HTTP header 取</b>（{@code X-Session-Id}，方案 §2.8 的推荐方案），
 * 由 agent 侧按用户注入，LLM 看不见也填不了。取不到时见
 * {@link SceneProperties#isSingleSessionFallback()}。</p>
 *
 * <p><b>返回值的限定语不能省：</b>{@code dispatched} 只表示帧已送到页面，UE 不回执。
 * 少了这句，模型会对用户说"已切换到 3 楼"而屏幕没动（方案 §2.5 第 3 点）。</p>
 */
@Slf4j
@Service
public class SceneMcp {

    private static final String TOOL_NAME_PREFIX = "scene_";

    /** 传输层元数据键：HTTP header {@code X-Session-Id} 的值。 */
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    private static final String TRANSPORT_KEY_SESSION_ID = "sessionId";

    /** 每次成功下发都要附上的限定语。 */
    private static final String DISPATCHED_CAVEAT =
        "\n注意：这只代表动作帧已送到场景页面，UE 不回执，不代表场景已经改变。"
            + "回复用户时不要说\"已完成\"，只能说\"已下发\"。";

    private final SceneDispatcher dispatcher;
    private final SceneSessionRegistry registry;

    public SceneMcp(SceneDispatcher dispatcher, SceneSessionRegistry registry) {
        this.dispatcher = dispatcher;
        this.registry = registry;
    }

    // ==================================================================
    // 楼层 / 视角
    // ==================================================================

    @McpTool(name = TOOL_NAME_PREFIX + "switch_floor", description = """
        切换到指定楼层（可选把视角聚焦过去）。用户说"切到N楼""去N层""看整栋楼""切到室外"时用它。
        """ + SceneActions.FLOOR_SEMANTICS)
    public String switchFloor(
        @McpToolParam(description = "目标楼层，见上面的楼层语义") Integer floor,
        @McpToolParam(description = "是否把相机聚焦到该楼层，默认 true", required = false) Boolean focus,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SWITCH_FLOOR, new Params()
            .put(SceneActions.PARAM_FLOOR, floor)
            .put(SceneActions.PARAM_FOCUS, focus));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "reset_view", description = """
        重置相机视角到初始位置（回到默认全景）。用户说"重置视角""回到默认视角""复位"时用它。
        """)
    public String resetView(McpSyncRequestContext context) {
        return call(context, SceneActions.RESET_VIEW, new Params());
    }

    @McpTool(name = TOOL_NAME_PREFIX + "recover_view", description = """
        恢复上一个视角。用户说"返回""退回去""恢复视角"时用它。
        """)
    public String recoverView(McpSyncRequestContext context) {
        return call(context, SceneActions.RECOVER_VIEW, new Params());
    }

    @McpTool(name = TOOL_NAME_PREFIX + "close_detail", description = """
        关闭当前打开的业务弹窗并收回视角（相当于用户手动点弹窗的关闭按钮）。
        用户说"关掉""关闭弹窗""退出详情"时用它。
        """)
    public String closeDetail(McpSyncRequestContext context) {
        return call(context, SceneActions.CLOSE_DETAIL, new Params());
    }

    @McpTool(name = TOOL_NAME_PREFIX + "reset_all", description = """
        全部重置：视角回到楼栋全景、关掉所有专题图层和业务弹窗，把场景清回初始状态。
        用户说"清场""全部关掉""回到初始状态""把刚才显示的都关了"时用它。
        """)
    public String resetAll(McpSyncRequestContext context) {
        return call(context, SceneActions.RESET_ALL, new Params());
    }

    // ==================================================================
    // 组合动作
    // ==================================================================

    @McpTool(name = TOOL_NAME_PREFIX + "show_visitor_track", description = """
        展示访客轨迹：切到楼层、高亮并聚焦访客对象、打开访客卡。
        用户说"看访客""访客轨迹""访客去哪了"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showVisitorTrack(
        @McpToolParam(description = "访客所在的楼层，默认 1", required = false) Integer floor,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_VISITOR_TRACK, new Params()
            .put(SceneActions.PARAM_FLOOR, floor));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_camera", description = """
        展示某路监控：切到楼层、聚焦该摄像头点位、打开视频窗。
        用户说"看监控""调出摄像头""看XX的监控"时用它。tag 是摄像头点位标识（如设备编号）。""" +
        SceneActions.FLOOR_SEMANTICS)
    public String showCamera(
        @McpToolParam(description = "摄像头点位的 tag，必填") String tag,
        @McpToolParam(description = "摄像头所在楼层", required = false) Integer floor,
        @McpToolParam(description = "应用标识，原样透传给前端", required = false) String app,
        @McpToolParam(description = "视频流标识，原样透传给前端", required = false) String stream,
        @McpToolParam(description = "视频窗标题，不传则由前端决定", required = false) String name,
        @McpToolParam(description = "视频源地址，原样透传给前端", required = false) String source_url,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_CAMERA, new Params()
            .put(SceneActions.PARAM_TAG, tag)
            .put(SceneActions.PARAM_FLOOR, floor)
            .put("app", app)
            .put("stream", stream)
            .put("name", name)
            .put("source_url", source_url));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_inspection", description = """
        展示巡检：切到楼层、高亮巡检对象、打开巡检卡。
        用户说"看巡检""巡检情况""巡检路线"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showInspection(
        @McpToolParam(description = "巡检所在楼层，默认 1", required = false) Integer floor,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_INSPECTION, new Params()
            .put(SceneActions.PARAM_FLOOR, floor));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_meeting_rooms", description = """
        展示会议室：切到楼层、高亮会议室分组、聚焦会议室相机。
        用户说"看会议室""会议室在哪""会议室情况"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showMeetingRooms(
        @McpToolParam(description = "会议室所在楼层，默认 3", required = false) Integer floor,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_MEETING_ROOMS, new Params()
            .put(SceneActions.PARAM_FLOOR, floor));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_dining", description = """
        展示食堂：切到楼层、高亮食堂区域并聚焦。
        用户说"看食堂""食堂在哪""去食堂"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showDining(
        @McpToolParam(description = "食堂所在楼层，默认 1", required = false) Integer floor,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_DINING, new Params()
            .put(SceneActions.PARAM_FLOOR, floor));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_area_overview", description = """
        展示防区总览：切到楼层、聚焦防区总览、高亮防区、逐个显示指定防区的监控点位。
        用户说"看防区""防区总览""哪些防区"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showAreaOverview(
        @McpToolParam(description = "防区所在楼层，默认 11（整栋楼）", required = false) Integer floor,
        @McpToolParam(description = "要高亮的防区标识列表，可空", required = false) List<String> tags,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_AREA_OVERVIEW, new Params()
            .put(SceneActions.PARAM_FLOOR, floor)
            .put(SceneActions.PARAM_TAGS, tags));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_device_category", description = """
        按设备类目显示点位：先隐藏已有的 POI，再切楼层，然后显示该楼层上这一类目下的所有点位。
        用户说"看XX设备""显示所有摄像头/烟感/门禁"时用它。category 是设备类目名（如 JIANKONG）。""" +
        SceneActions.FLOOR_SEMANTICS)
    public String showDeviceCategory(
        @McpToolParam(description = "设备类目名，如 JIANKONG，必填") String category,
        @McpToolParam(description = "限定楼层，不传则由前端决定", required = false) Integer floor,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_DEVICE_CATEGORY, new Params()
            .put("category", category)
            .put(SceneActions.PARAM_FLOOR, floor));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "show_fusion", description = """
        展示虚实融合视频：切到楼层后把指定 tag 的实时视频融合到场景里。
        用户说"融合""虚实融合""把视频投到场景里"时用它。""" + SceneActions.FLOOR_SEMANTICS)
    public String showFusion(
        @McpToolParam(description = "融合目标的 tag，必填") String tag,
        @McpToolParam(description = "楼栋名，默认 室外", required = false) String build,
        @McpToolParam(description = "楼层，默认 13（室外）", required = false) Integer floorNum,
        @McpToolParam(description = "附加选项，原样透传给前端", required = false) String options,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SHOW_FUSION, new Params()
            .put(SceneActions.PARAM_TAG, tag)
            .put(SceneActions.PARAM_BUILD, build)
            .put(SceneActions.PARAM_FLOOR_NUM, floorNum)
            .put("options", options));
    }

    // ==================================================================
    // 原子动作（补组合未覆盖的场景）
    // ==================================================================

    @McpTool(name = TOOL_NAME_PREFIX + "focus_actor", description = """
        把相机聚焦到某个对象上，并让该对象显示出来。
        比组合动作更底层，只在没有合适组合动作时使用。
        用户说"聚焦到XX""拉近看XX""对准XX"时用它。
        """)
    public String focusActor(
        @McpToolParam(description = "对象大类，如 BASEACTOR / POI / AREA") String actorType,
        @McpToolParam(description = "对象子类，如 JIANKONG / MEETING_ROOM") String type,
        @McpToolParam(description = "对象标识") String tag,
        McpSyncRequestContext context) {

        return call(context, SceneActions.FOCUS_ACTOR, new Params()
            .put(SceneActions.PARAM_ACTOR_TYPE, actorType)
            .put(SceneActions.PARAM_TYPE, type)
            .put(SceneActions.PARAM_TAG, tag));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "set_actor_visible", description = """
        显示或隐藏某个对象（不改视角）。需要精确控制某个对象的显隐时用它。
        注意：隐藏全部 POI 请用 set_poi_visible。
        """)
    public String setActorVisible(
        @McpToolParam(description = "对象大类，如 BASEACTOR / POI / AREA") String actorType,
        @McpToolParam(description = "对象子类，如 JIANKONG / MEETING_ROOM") String type,
        @McpToolParam(description = "对象标识") String tag,
        @McpToolParam(description = "true=显示（ViewActor），false=隐藏（HideActor）") Boolean visible,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SET_ACTOR_VISIBLE, new Params()
            .put(SceneActions.PARAM_ACTOR_TYPE, actorType)
            .put(SceneActions.PARAM_TYPE, type)
            .put(SceneActions.PARAM_TAG, tag)
            .put(SceneActions.PARAM_VISIBLE, visible));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "set_poi_visible", description = """
        显示某一类 POI 点位，或隐藏全部 POI。
        visible=false 时忽略其他参数，直接隐藏全部 POI（前端走 HidePOI）。
        用户说"显示XX点位""关掉点位""清掉所有标记"时用它。
        """)
    public String setPoiVisible(
        @McpToolParam(description = "POI 类目，visible=true 时必填") String type,
        @McpToolParam(description = "楼栋，visible=true 时必填") String build,
        @McpToolParam(description = "楼层，visible=true 时必填") Integer floorNum,
        @McpToolParam(description = "true=显示该类目，false=隐藏全部 POI") Boolean visible,
        McpSyncRequestContext context) {

        boolean show = Boolean.TRUE.equals(visible);
        Params params = new Params().put(SceneActions.PARAM_VISIBLE, visible);
        if (show) {
            params.put(SceneActions.PARAM_TYPE, type)
                .put(SceneActions.PARAM_BUILD, build)
                .put(SceneActions.PARAM_FLOOR_NUM, floorNum);
        }
        return call(context, SceneActions.SET_POI_VISIBLE, params);
    }

    @McpTool(name = TOOL_NAME_PREFIX + "set_env", description = """
        调整场景的环境效果：时间或天气。两个参数至少要填一个，也可以同时填。
        用户说"天黑了""改成傍晚""下雨""放晴"时用它。
        """)
    public String setEnv(
        @McpToolParam(description = "环境时间，如 白天/傍晚/夜晚，具体取值由前端定义", required = false) String env_time,
        @McpToolParam(description = "环境天气，如 晴/雨/雪，具体取值由前端定义", required = false) String env_weather,
        McpSyncRequestContext context) {

        if ((env_time == null || env_time.isBlank()) && (env_weather == null || env_weather.isBlank())) {
            return "调用 scene_set_env 失败：env_time 和 env_weather 至少要填一个。";
        }
        return call(context, SceneActions.SET_ENV, new Params()
            .put(SceneActions.PARAM_ENV_TIME, env_time)
            .put(SceneActions.PARAM_ENV_WEATHER, env_weather));
    }

    // ==================================================================
    // 破坏性动作（Phase 4 接确认卡片前不执行，见 scene.destructive-enabled）
    // ==================================================================

    @McpTool(name = TOOL_NAME_PREFIX + "set_area_state", description = """
        设置防区状态（离线 / 布防 / 撤防 / 告警）。这是会改变现场状态的动作。
        用户说"把XX防区撤防""给XX布防"时用它。
        """)
    public String setAreaState(
        @McpToolParam(description = "防区标识") String tag,
        @McpToolParam(description = "目标状态") SceneActions.AreaState state,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SET_AREA_STATE, new Params()
            .put(SceneActions.PARAM_TAG, tag)
            .put(SceneActions.PARAM_STATE, state == null ? null : state.value()));
    }

    @McpTool(name = TOOL_NAME_PREFIX + "set_inspection_state", description = """
        控制巡检任务（开始 / 暂停 / 结束）。这是会改变现场状态的动作。
        用户说"开始巡检""暂停巡检""结束巡检"时用它。
        """)
    public String setInspectionState(
        @McpToolParam(description = "巡检任务标识") String tag,
        @McpToolParam(description = "目标状态") SceneActions.InspectionState state,
        McpSyncRequestContext context) {

        return call(context, SceneActions.SET_INSPECTION_STATE, new Params()
            .put(SceneActions.PARAM_TAG, tag)
            .put(SceneActions.PARAM_STATE, state == null ? null : state.value()));
    }

    // ==================================================================
    // 查询类（第 19 个工具，不下发动作）
    // ==================================================================

    @McpTool(name = "get_scene_state", description = """
        查询场景页面当前的状态快照：是否就绪、当前在哪一层、哪栋楼、可选楼层列表。
        判断"用户现在看的是第几层"只能靠这个工具，不要靠你上一轮说过什么 ——
        用户可能在页面里自己点了菜单切了楼层。
        无参数，不推送动作，只是读缓存。
        """)
    public String getSceneState(McpSyncRequestContext context) {
        String headerSessionId = sessionIdOf(context);
        SceneSession session = registry.resolve(headerSessionId);
        if (session == null) {
            return "场景页面未连接，拿不到状态。请提示用户先打开场景页。";
        }
        String snapshot = session.getSceneStateJson();
        if (snapshot == null) {
            return "场景页面已连接，但还没有上报过状态快照（可能正在加载）。";
        }
        return snapshot;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private String call(McpSyncRequestContext context, String action, Params params) {
        Map<String, Object> values = params.build();
        SceneOutcome outcome = dispatcher.dispatch(sessionIdOf(context), action, values);

        if (outcome.ok()) {
            return "已下发动作 " + action + describeParams(values) + "。" + DISPATCHED_CAVEAT;
        }
        return "调用 " + action + describeParams(values) + " 失败：" + outcome.error()
            + "（这个动作没有下发成功，不要告诉用户已经执行了）";
    }

    private static String describeParams(Map<String, Object> values) {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("（");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append('）').toString();
    }

    /**
     * 从传输层元数据里取 agent 注入的 {@code X-Session-Id}。
     *
     * <p>取不到时返回 null，由 {@code SceneSessionRegistry.resolve} 决定是走单会话兜底还是报错 ——
     * <b>绝不让 LLM 填这个值</b>（方案 §2.8）。</p>
     */
    private String sessionIdOf(McpSyncRequestContext context) {
        if (context == null) {
            return null;
        }
        McpTransportContext transportContext = context.transportContext();
        if (transportContext == null) {
            return null;
        }
        Object value = transportContext.get(TRANSPORT_KEY_SESSION_ID);
        return value == null ? null : value.toString();
    }

    /** 跳过 null 的参数收集器：null 一律不下发，让前端用自己的默认值。 */
    private static final class Params {

        private final Map<String, Object> values = new LinkedHashMap<>();

        Params put(String key, Object value) {
            if (value != null) {
                values.put(key, value);
            }
            return this;
        }

        Map<String, Object> build() {
            return values;
        }
    }
}