package com.hx.mcpsidecar.service.mcp.ragflow;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索 MCP 工具集 - 来自 RAGFlow 知识库平台。
 *
 * 对 Agent 暴露两个工具：
 *   - ragflow:searchKnowledge  按问题检索知识库，返回带来源的文本块
 *   - ragflow:listDatasets     列出全部知识库（ID、名称、文档数），用于确定检索范围
 *
 * 描述文案是 Agent 路由的关键：正向触发场景 + 典型问句 + 反例三样写全，
 * 让模型在"知识问答"和"实时态势查询"之间能选对路（实时数据走 person_status 等工具）。
 *
 * 返回值约定：工具方法内部把异常兜底成中文提示文案返回给大模型，不往外抛原始异常——
 * MCP 工具抛异常时部分客户端只显示"调用失败"，模型拿不到可继续推理的信息。
 */
@Slf4j
@Service
public class RagflowKnowledgeMcp {

    /** 工具名前缀，与 person_status:、path_find_service: 等既有工具的命名风格保持一致 */
    private static final String TOOL_NAME_PREFIX = "ragflow:";

    /** 工具描述前缀，与"人员态势服务:"等既有风格保持一致 */
    private static final String TOOL_DESC_PREFIX = "知识库检索服务:";

    @Autowired
    private RagflowApiClient ragflowApiClient;

    /**
     * 知识库检索工具。
     *
     * @param question   用户的原始问题，尽量保留完整问句，不要自行提炼成关键词
     * @param datasetIds 可选。指定要检索的知识库ID，多个用英文逗号分隔；
     *                   不传或传空则检索配置的默认范围（yml 的 datasetIds，未配置时为全库）
     * @return 带来源标注的检索结果文本；检索不到或服务异常时返回明确的中文说明
     */
    @McpTool(
            name = TOOL_NAME_PREFIX + "searchKnowledge",
            description = TOOL_DESC_PREFIX + """
                        查询园区规章制度、管理办法、操作规范、说明文档等知识库内容。
                        当用户询问"XX制度是什么/怎么规定/流程是什么/标准是多少"这类静态知识问题时调用。
                        示例："食品留样要保存几小时？"、"外来人员入园需要什么手续？"、"宿舍用电有什么规定？"
                        不要用于查询实时动态数据（在园人数、设备状态等有专门工具）。
                        datasetIds参数可选：指定知识库ID（多个用英文逗号分隔），不传则检索全部知识库。
                        """
    )
    public String searchKnowledge(String question, String datasetIds) {
        // 入参校验：空问题直接返回引导文案，不打无意义的检索请求
        if (!StringUtils.hasText(question)) {
            return "问题不能为空，请输入要查询的知识内容。";
        }
        try {
            // 解析第二个参数：兼容 null、空串、单个ID、逗号分隔多个ID
            List<String> ids = new ArrayList<>();
            if (StringUtils.hasText(datasetIds)) {
                for (String id : datasetIds.split(",")) {
                    if (StringUtils.hasText(id)) {
                        ids.add(id.trim());
                    }
                }
            }

            // 调 RAGFlow 检索接口，拿到原始响应
            JSONObject resp = ragflowApiClient.retrieval(question, ids);
            JSONObject data = resp.getJSONObject("data");
            JSONArray chunks = data == null ? null : data.getJSONArray("chunks");

            // 空结果必须"会说没有"：给模型明确的转向信号，它才能体面地转其他意图或走兜底话术，
            // 而不是硬编一个答案出来
            if (chunks == null || chunks.isEmpty()) {
                return "未在知识库中检索到与\"" + question + "\"相关的内容，建议更换问法重试，或转接其他服务处理。";
            }

            // 拼装模型易读的格式：序号 + 来源文档 + 相似度 + 内容。
            // 标注来源是为了让回答能附引用出处，相似度供模型判断内容可信程度
            StringBuilder sb = new StringBuilder();
            sb.append("共检索到 ").append(chunks.size()).append(" 条与\"")
              .append(question).append("\"相关的内容：\n\n");
            for (int i = 0; i < chunks.size(); i++) {
                JSONObject chunk = chunks.getJSONObject(i);
                String content = ragflowApiClient.truncate(chunk.getString("content"));
                String docName = chunk.getString("document_keyword");
                Double similarity = chunk.getDouble("similarity");

                sb.append("【").append(i + 1).append("】");
                if (StringUtils.hasText(docName)) {
                    sb.append("(来源: ").append(docName);
                    if (similarity != null) {
                        sb.append(", 相关度: ").append(String.format("%.2f", similarity));
                    }
                    sb.append(")");
                }
                sb.append("\n").append(content).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            // 兜底：任何异常都转成模型可读的文案，并记录完整日志供排查
            log.error("知识库检索工具执行失败, question={}", question, e);
            return "知识库服务暂时不可用，请稍后重试。错误信息: " + e.getMessage();
        }
    }

    /**
     * 知识库列表工具。零参数，供 Agent（和人）确认有哪些知识库、拿 datasetId。
     *
     * @return 每行一个知识库：名称、ID、文档数、切分块数；服务异常时返回明确的中文说明
     */
    @McpTool(
            name = TOOL_NAME_PREFIX + "listDatasets",
            description = TOOL_DESC_PREFIX + """
                        列出所有可检索的知识库，返回知识库名称、ID、文档数量。
                        当需要确定某个知识库的ID（datasetId）以便指定检索范围时调用。
                        """
    )
    public String listDatasets() {
        try {
            JSONObject resp = ragflowApiClient.listDatasets();
            JSONArray datasets = resp.getJSONArray("data");

            if (datasets == null || datasets.isEmpty()) {
                return "当前没有可访问的知识库，请先在 RAGFlow 平台上创建知识库并上传解析文档。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("共有 ").append(datasets.size()).append(" 个知识库：\n");
            for (int i = 0; i < datasets.size(); i++) {
                JSONObject ds = datasets.getJSONObject(i);
                sb.append("【").append(i + 1).append("】")
                  .append("名称: ").append(ds.getString("name"))
                  .append(" | ID: ").append(ds.getString("id"))
                  .append(" | 文档数: ").append(ds.getIntValue("document_count"))
                  .append(" | 知识块数: ").append(ds.getIntValue("chunk_num"))
                  .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("知识库列表工具执行失败", e);
            return "知识库服务暂时不可用，请稍后重试。错误信息: " + e.getMessage();
        }
    }
}
