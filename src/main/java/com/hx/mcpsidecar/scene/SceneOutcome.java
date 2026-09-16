package com.hx.mcpsidecar.scene;

/**
 * 一次动作下发的结局。
 *
 * <p>{@link #state} 取值来自协议（方案 §2.6）：{@code dispatched | awaiting_confirm | rejected}。
 * <b>没有 {@code ok}</b> —— UE 不回执，页面只能确认"帧已发出"，不能确认"UE 已执行"。
 * {@code dispatched} 因此是状态本身的取值，而不是 {@code ok + acknowledged:false} 这种恒假的伴随字段。</p>
 *
 * @param state  见上
 * @param error  给 LLM 看的可读错误说明；成功时为 {@code null}
 * @param detail 出错时的补充信息（例如 {@code BAD_PARAM} 里那个 key），成功时为 {@code null}
 */
public record SceneOutcome(String state, String error, String detail) {

    public static final String DISPATCHED = "dispatched";
    public static final String AWAITING_CONFIRM = "awaiting_confirm";
    public static final String REJECTED = "rejected";

    public static SceneOutcome dispatched() {
        return new SceneOutcome(DISPATCHED, null, null);
    }

    public static SceneOutcome rejected(String error) {
        return new SceneOutcome(REJECTED, error, null);
    }

    public static SceneOutcome rejected(String error, String detail) {
        return new SceneOutcome(REJECTED, error, detail);
    }

    /**
     * {@code dispatched} 只意味着"帧已送到页面"，不代表 UE 已经执行。
     */
    public boolean ok() {
        return DISPATCHED.equals(state) || AWAITING_CONFIRM.equals(state);
    }
}