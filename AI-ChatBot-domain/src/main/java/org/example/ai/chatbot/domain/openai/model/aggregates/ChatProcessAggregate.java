package org.example.ai.chatbot.domain.openai.model.aggregates;

import org.example.ai.chatbot.domain.openai.model.entity.MessageEntity;
import org.example.ai.chatbot.types.common.Constants;
import org.example.ai.chatbot.types.enums.ChatGLMModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description
 * @create 2023-07-22 21:09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatProcessAggregate {

    /** 验证信息 */
    private String openid;
    /** 默认模型 */
    private String model = ChatGLMModel.GLM_3_5_TURBO.getCode();
    /** 问题描述 */
    private List<MessageEntity> messages;

    public boolean isWhiteList(String whiteListStr) {
        String[] whiteList = whiteListStr.split(Constants.SPLIT);
        for (String whiteOpenid : whiteList) {
            if (whiteOpenid.equals(openid)) return true;
        }
        return false;
    }

}
