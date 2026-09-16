package com.hx.mcpsidecar.scene;

import java.util.Set;

/**
 * 场景动作词表（方案 §4，19 个封闭集合）。
 *
 * <p><b>这里是前后端对齐的唯一落点。</b> {@link com.hx.mcpsidecar.scene.SceneDispatcher}
 * 下发的 {@code name} 必须与前端 {@code src/utils/uePlayer/sceneExecutor.ts} 的词表逐字一致，
 * 对不上时前端会回 {@code UNKNOWN_ACTION:<name>}。改词表只需要改这个类 +
 * {@code SceneMcp} 里对应方法的 {@code @McpTool(name = ...)}。</p>
 *
 * <p><b>前端自带铺场，sidecar 不要重复下发 {@code ViewActor}。</b>
 * 执行器的每条 {@code FocusTo} 前面都自己补了 {@code ViewActor} 把目标显出来
 * （总览页自己不碰场景，用户对话时场景里可能什么都没有、也可能残留着上一个动作的图层）。
 * 所以想让某个点位可见，调 {@code scene_focus_actor} 就够了，
 * <b>不要</b>额外再调 {@code scene_set_actor_visible} 去铺场。</p>
 */
public final class SceneActions {

    private SceneActions() {
    }

    // ---------- 楼层 / 视角 ----------

    public static final String SWITCH_FLOOR = "scene_switch_floor";
    public static final String RESET_VIEW = "scene_reset_view";
    public static final String RECOVER_VIEW = "scene_recover_view";
    public static final String CLOSE_DETAIL = "scene_close_detail";

    /**
     * 全部重置：视角回默认 + 清掉 7 类专题图层 + 关弹窗。用于"清场/回到初始状态"。
     *
     * <p>内部走 {@code showFloor(99)}：{@code >=11} 分支自带 {@code ResetView}（显示楼栋、隐藏楼层），
     * 所以不需要另外发 {@code ResetView}。用 99 而不是 11 —— {@code FloorSelecter} 拿
     * {@code appState.floor} 与那 11 项比对，99 匹配不上所以全部不高亮；用 11 会让"楼栋"呈选中态。</p>
     *
     * <p>两个已知缺口：关不掉 {@code scene_show_fusion} 开的虚实融合窗（{@code HideActorById}
     * 的 id 运行时生成、无法枚举）；有专题页正挂载时它会 {@code watch(appState.floor)} 把
     * POI 填回来。</p>
     */
    public static final String RESET_ALL = "scene_reset_all";

    // ---------- 组合动作 ----------

    public static final String SHOW_VISITOR_TRACK = "scene_show_visitor_track";
    public static final String SHOW_CAMERA = "scene_show_camera";
    public static final String SHOW_INSPECTION = "scene_show_inspection";
    public static final String SHOW_MEETING_ROOMS = "scene_show_meeting_rooms";
    public static final String SHOW_DINING = "scene_show_dining";
    public static final String SHOW_AREA_OVERVIEW = "scene_show_area_overview";
    public static final String SHOW_DEVICE_CATEGORY = "scene_show_device_category";
    public static final String SHOW_FUSION = "scene_show_fusion";

    // ---------- 原子 ----------

    public static final String FOCUS_ACTOR = "scene_focus_actor";
    public static final String SET_ACTOR_VISIBLE = "scene_set_actor_visible";
    public static final String SET_POI_VISIBLE = "scene_set_poi_visible";
    public static final String SET_ENV = "scene_set_env";

    // ---------- 破坏性（Phase 4 接确认卡片前不执行，见 SceneProperties.destructiveEnabled） ----------

    public static final String SET_AREA_STATE = "scene_set_area_state";
    public static final String SET_INSPECTION_STATE = "scene_set_inspection_state";

    /** 全部合法动作名。sidecar 只下发这张表里的名字，表外一律不下发。 */
    public static final Set<String> ALL = Set.of(
        SWITCH_FLOOR, RESET_VIEW, RECOVER_VIEW, CLOSE_DETAIL, RESET_ALL,
        SHOW_VISITOR_TRACK, SHOW_CAMERA, SHOW_INSPECTION, SHOW_MEETING_ROOMS,
        SHOW_DINING, SHOW_AREA_OVERVIEW, SHOW_DEVICE_CATEGORY, SHOW_FUSION,
        FOCUS_ACTOR, SET_ACTOR_VISIBLE, SET_POI_VISIBLE, SET_ENV,
        SET_AREA_STATE, SET_INSPECTION_STATE
    );

    /** 需要用户确认后才执行的破坏性动作。 */
    public static final Set<String> DESTRUCTIVE = Set.of(SET_AREA_STATE, SET_INSPECTION_STATE);

    /** 楼层语义说明，拼进每个涉及楼层的工具描述里。 */
    public static final String FLOOR_SEMANTICS = """
        楼层为整数：-1=B1，1~N 为具体楼层，99=整栋楼。
        不要用 1 表达"室外"，也不要用 0 表达"整栋楼"。""";

    /**
     * 楼栋视图的楼层值。{@code scene_reset_all} 内部的 {@code showFloor} 用它。
     *
     * <p>前端 {@code useSplitBuild} 以 {@code >=11} 判"楼栋视图"，所以 11 和 99
     * <b>效果一样</b>，差别只在 {@code FloorSelecter} 的高亮：11 会让"楼栋"按钮呈选中态，
     * 99 匹配不上那 11 项所以全部不高亮。清场场景要"看不出选中了哪层"，所以用 99。</p>
     */
    public static final int FLOOR_WHOLE_BUILDING = 99;

    // ---------- 参数名（前后端对齐用；前端 params 的 key） ----------

    public static final String PARAM_FLOOR = "floor";
    public static final String PARAM_FLOOR_NUM = "floorNum";
    public static final String PARAM_FOCUS = "focus";
    public static final String PARAM_TAG = "tag";
    public static final String PARAM_TAGS = "tags";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_ACTOR_TYPE = "actorType";
    public static final String PARAM_BUILD = "build";
    public static final String PARAM_VISIBLE = "visible";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_ENV_TIME = "env_time";
    public static final String PARAM_ENV_WEATHER = "env_weather";

    /** 楼层字段名不统一：POI 类目用 floorNum，其余用 floor（方案 §4 参数形状坑）。 */
    public static boolean isValid(String name) {
        return name != null && ALL.contains(name);
    }

    // ---------- 中文状态字面量 ----------
    //
    // 方案 §4：中文状态字面量必须在工具 schema 里约束取值（enum），别指望前端 ——
    // 写错的中文值会被 UE 静默忽略。
    //
    // 常量名直接用中文，不是图省事：工具 schema 由 spring-ai 反射生成，它读的是枚举
    // 常量名，既不看 @JsonProperty 也不看 value()（实测见
    // SceneChannelIntegrationTest#exposesToolsWithEnumConstrainedState）。
    // 只有"常量名 == 下发字面量"，schema 里列出的取值才和真正下发的东西一致；
    // 把常量名改回 ARMED/DISARMED 会让 schema 对模型撒谎，模型就会填出工具收不了的值。

    /** {@code scene_set_area_state} 的 state 取值，常量名即下发字面量。 */
    public enum AreaState {
        离线, 布防, 撤防, 告警;

        /** 下发给前端的字面量。 */
        public String value() {
            return name();
        }
    }

    /** {@code scene_set_inspection_state} 的 state 取值，常量名即下发字面量。 */
    public enum InspectionState {
        开始, 暂停, 结束;

        /** 下发给前端的字面量。 */
        public String value() {
            return name();
        }
    }
}