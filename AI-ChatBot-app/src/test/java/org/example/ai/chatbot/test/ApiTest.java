package org.example.ai.chatbot.test;


import cn.bugstack.chatglm.model.*;
import cn.bugstack.chatglm.session.Configuration;
import cn.bugstack.chatglm.session.OpenAiSession;
import cn.bugstack.chatglm.session.OpenAiSessionFactory;
import cn.bugstack.chatglm.session.defaults.DefaultOpenAiSessionFactory;
import cn.bugstack.chatglm.utils.BearerTokenUtils;
import okhttp3.Response;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

//    @Resource
    private OpenAiSession openAiSession;

    @Before
    public void test_OpenAiSessionFactory() {
        // 1. 配置文件
        Configuration configuration = new Configuration();
        configuration.setApiHost("https://open.bigmodel.cn/");
        configuration.setApiSecretKey("d847050042570e7136f926a7d3cc3e83.UMIVEHtoforykzFU");
        configuration.setLevel(HttpLoggingInterceptor.Level.BODY);
        // 2. 会话工厂
        OpenAiSessionFactory factory = new DefaultOpenAiSessionFactory(configuration);
        // 3. 开启会话
        this.openAiSession = factory.openSession();
    }

    @Test
    public void test_chat_completions() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // 入参；模型、请求信息
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(Model.GLM_4); // chatGLM_6b_SSE、chatglm_lite、chatglm_lite_32k、chatglm_std、chatglm_pro
        request.setIsCompatible(false); // 是否对返回结果数据做兼容，24年1月发布的 GLM_3_5_TURBO、GLM_4 模型，与之前的模型在返回结果上有差异。开启 true 可以做兼容。
        // 24年1月发布的 glm-3-turbo、glm-4 支持函数、知识库、联网功能
        request.setTools(new ArrayList<ChatCompletionRequest.Tool>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Tool.builder()
                        .type(ChatCompletionRequest.Tool.Type.web_search)
                        .webSearch(ChatCompletionRequest.Tool.WebSearch.builder().enable(true).searchQuery("小傅哥").build())
                        .build());
            }
        });
        request.setPrompt(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content("write a merge sort")
                        .build());
            }
        });

        // 请求
        openAiSession.completions(request, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                ChatCompletionResponse response = JSON.parseObject(data, ChatCompletionResponse.class);
                log.info("测试结果 onEvent：{}", response.getData());
                // type 消息类型，add 增量，finish 结束，error 错误，interrupted 中断
                if (EventType.finish.getCode().equals(type)) {
                    ChatCompletionResponse.Meta meta = JSON.parseObject(response.getMeta(), ChatCompletionResponse.Meta.class);
                    log.info("[输出结束] Tokens {}", JSON.toJSONString(meta));
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.info("对话完成");
                countDownLatch.countDown();
            }

            @Override
            public void onFailure(EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                log.info("对话异常");
                countDownLatch.countDown();
            }
        });

        // 等待
        countDownLatch.await();
    }

    /**
     * 此对话模型 3.5 接近于官网体验 & 流式应答
     */
    @Test
    public void test_chat_completions_stream() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // 入参；模型、请求信息
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(Model.GLM_4V); // GLM_3_5_TURBO、GLM_4
        request.setIsCompatible(false);
        // 24年1月发布的 glm-3-turbo、glm-4 支持函数、知识库、联网功能
        request.setTools(new ArrayList<ChatCompletionRequest.Tool>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Tool.builder()
                        .type(ChatCompletionRequest.Tool.Type.web_search)
                        .webSearch(ChatCompletionRequest.Tool.WebSearch.builder().enable(true).searchQuery("小傅哥").build())
                        .build());
            }
        });
        request.setMessages(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content("小傅哥的是谁")
                        .build());
            }
        });

        // 请求
        openAiSession.completions(request, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                if ("[DONE]".equals(data)) {
                    log.info("[输出结束] Tokens {}", JSON.toJSONString(data));
                    return;
                }

                ChatCompletionResponse response = JSON.parseObject(data, ChatCompletionResponse.class);
                log.info("测试结果：{}", JSON.toJSONString(response));
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.info("对话完成");
                countDownLatch.countDown();
            }

            @Override
            public void onFailure(EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                log.error("对话失败", t);
                countDownLatch.countDown();
            }
        });

        // 等待
        countDownLatch.await();
    }

//    @Test
//    public void test_completions_4() throws Exception {
//        CountDownLatch countDownLatch = new CountDownLatch(1);
//        // 入参；模型、请求信息
//        ChatCompletionRequest request = new ChatCompletionRequest();
//        request.setModel(Model.GLM_4_Flash); // GLM_4_Flash 等模型校验
//        request.setStream(true);
//
//        request.setMessages(new ArrayList<ChatCompletionRequest.Prompt>() {
//            private static final long serialVersionUID = -7988151926241837899L;
//
//            {
//                // content 字符串格式
//                add(ChatCompletionRequest.Prompt.builder()
//                        .role(Role.user.getCode())
//                        .content("1+1")
//                        .build());
//            }
//        });
//
//        openAiSession.completions(request, new EventSourceListener() {
//            @Override
//            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
//                if ("[DONE]".equals(data)) {
//                    log.info("[输出结束] Tokens {}", JSON.toJSONString(data));
//                    return;
//                }
//
//                ChatCompletionResponse response = JSON.parseObject(data, ChatCompletionResponse.class);
//                log.info("测试结果：{}", JSON.toJSONString(response));
//            }
//
//            @Override
//            public void onClosed(EventSource eventSource) {
//                log.info("对话完成");
//                countDownLatch.countDown();
//            }
//
//            @Override
//            public void onFailure(EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
//                log.error("对话失败", t);
//                countDownLatch.countDown();
//            }
//        });
//
//        // 等待
//        countDownLatch.await();
//
//    }

    /**
     * 模型编码：glm-4v
     * 根据输入的自然语言指令和图像信息完成任务，推荐使用 SSE 或同步调用方式请求接口
     * https://open.bigmodel.cn/dev/api#glm-4v
     */
    @Test
    public void test_completions_4v() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // 入参；模型、请求信息
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(Model.GLM_4V); // GLM_3_5_TURBO、GLM_4
        request.setStream(true);
        request.setMessages(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                // content 字符串格式
//                add(ChatCompletionRequest.Prompt.builder()
//                        .role(Role.user.getCode())
//                        .content("这个图片写了什么")
//                        .build());

                // content 对象格式
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content(ChatCompletionRequest.Prompt.Content.builder()
                                .type(ChatCompletionRequest.Prompt.Content.Type.text.getCode())
                                .text("这是什么图片")
                                .build())
                        .build());

                // content 对象格式，上传图片；图片支持url、basde64
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content(ChatCompletionRequest.Prompt.Content.builder()
                                .type(ChatCompletionRequest.Prompt.Content.Type.image_url.getCode())
                                .imageUrl(ChatCompletionRequest.Prompt.Content.ImageUrl.builder().url("https://bugstack.cn/images/article/project/chatgpt/chatgpt-extra-231011-01.png").build())
                                .build())
                        .build());
            }
        });

        openAiSession.completions(request, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                if ("[DONE]".equals(data)) {
                    log.info("[输出结束] Tokens {}", JSON.toJSONString(data));
                    return;
                }

                ChatCompletionResponse response = JSON.parseObject(data, ChatCompletionResponse.class);
                log.info("测试结果：{}", JSON.toJSONString(response));
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.info("对话完成");
                countDownLatch.countDown();
            }

            @Override
            public void onFailure(EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                log.error("对话失败", t);
                countDownLatch.countDown();
            }
        });

        // 等待
        countDownLatch.await();

    }

    /**
     * 同步请求
     */
    @Test
    public void test_completions_future() throws Exception {
        // 入参；模型、请求信息
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(Model.CHATGLM_TURBO); // chatGLM_6b_SSE、chatglm_lite、chatglm_lite_32k、chatglm_std、chatglm_pro
        request.setPrompt(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content("1+1")
                        .build());
            }
        });

        CompletableFuture<String> future = openAiSession.completions(request);
        String response = future.get();

        log.info("测试结果：{}", response);
    }

    /**
     * 同步请求
     */
    @Test
    public void test_completions_sync_01() throws Exception {
        // 入参；模型、请求信息
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(Model.GLM_3_5_TURBO); // chatGLM_6b_SSE、chatglm_lite、chatglm_lite_32k、chatglm_std、chatglm_pro
        request.setPrompt(new ArrayList<ChatCompletionRequest.Prompt>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Prompt.builder()
                        .role(Role.user.getCode())
                        .content("小傅哥是谁")
                        .build());
            }
        });

        // 24年1月发布的 glm-3-turbo、glm-4 支持函数、知识库、联网功能
        request.setTools(new ArrayList<ChatCompletionRequest.Tool>() {
            private static final long serialVersionUID = -7988151926241837899L;

            {
                add(ChatCompletionRequest.Tool.builder()
                        .type(ChatCompletionRequest.Tool.Type.web_search)
                        .webSearch(ChatCompletionRequest.Tool.WebSearch.builder().enable(true).searchQuery("小傅哥").build())
                        .build());
            }
        });

        ChatCompletionSyncResponse response = openAiSession.completionsSync(request);

        log.info("测试结果：{}", JSON.toJSONString(response));
    }

//    @Test
//    public void test_completions_sync_02() throws Exception {
//        // 入参；模型、请求信息
//        ChatCompletionRequest request = new ChatCompletionRequest();
//        request.setModel(Model.GLM_3_5_TURBO); // chatGLM_6b_SSE、chatglm_lite、chatglm_lite_32k、chatglm_std、chatglm_pro
//        request.setPrompt(new ArrayList<ChatCompletionRequest.Prompt>() {
//            private static final long serialVersionUID = -7988151926241837899L;
//
//            {
//                add(ChatCompletionRequest.Prompt.builder()
//                        .role(Role.user.getCode())
//                        .content("1+1")
//                        .build());
//            }
//        });
//
//        ChatCompletionSyncResponse response = openAiSession.completionsSync(request);
//
//        log.info("测试结果：{}", JSON.toJSONString(response));
//        System.out.println(response.getChoices().get(0).getMessage().getContent());
//    }

    @Test
    public void test_genImages() throws Exception {
        ImageCompletionRequest request = new ImageCompletionRequest();
        request.setModel(Model.COGVIEW_3);
        request.setPrompt("画个小狗");
        ImageCompletionResponse response = openAiSession.genImages(request);
        log.info("测试结果：{}", JSON.toJSONString(response));
    }

    @Test
    public void test_curl() {
        // 1. 配置文件
        Configuration configuration = new Configuration();
        configuration.setApiHost("https://open.bigmodel.cn/");
        configuration.setApiSecretKey("39580e34e175019c230fdd519817b381.*****");

        // 2. 获取Token
        String token = BearerTokenUtils.getToken(configuration.getApiKey(), configuration.getApiSecret());
        log.info("1. 在智谱Ai官网，申请 ApiSeretKey 配置到此测试类中，替换 setApiSecretKey 值。 https://open.bigmodel.cn/usercenter/apikeys");
        log.info("2. 运行 test_curl 获取 token：{}", token);
        log.info("3. 将获得的 token 值，复制到 curl.sh 中，填写到 Authorization: Bearer 后面");
        log.info("4. 执行完步骤3以后，可以复制直接运行 curl.sh 文件，或者复制 curl.sh 文件内容到控制台/终端/ApiPost中运行");
    }

}
