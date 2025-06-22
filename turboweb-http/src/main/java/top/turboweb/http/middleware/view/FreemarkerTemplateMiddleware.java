package top.turboweb.http.middleware.view;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.aware.CharsetAware;
import top.turboweb.http.middleware.aware.MainClassAware;
import top.turboweb.http.response.ViewModel;
import top.turboweb.commons.exception.TurboTemplateRenderException;


import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * freemarker模板渲染中间件
 */
public class FreemarkerTemplateMiddleware extends TemplateMiddleware implements MainClassAware, CharsetAware {

    // 主启动类
    private Class<?> mainClass;
    // 模板文件的存储路径
    private String templatePath = "templates";
    // 模板文件的后缀
    private String templateSuffix = ".ftl";
    // 工程的编码
    private Charset charset;
    // 模板配置
    private Configuration configuration;
    // 是否缓存模板
    private boolean openCache = true;
    // 模板的缓存
    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    @Override
    public String render(HttpContext ctx, ViewModel viewModel) {
        String templateName = viewModel.getViewName();
        Template template = loadTemplate(templateName);
        // 获取数据
        Map<String, Object> attributes = viewModel.getAttributes();
        // 渲染模板
        try {
            StringWriter writer = new StringWriter();
            template.process(attributes, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new TurboTemplateRenderException("模板渲染失败", e);
        }
    }


    /**
     * 加载模板
     *
     * @param templateName 模板名称
     * @return 模板
     */
    public Template loadTemplate(String templateName) {
        String fullTemplateName = templateName + templateSuffix;
        try {
            if (!openCache) {
                return configuration.getTemplate(fullTemplateName);
            }
            // 从缓存中获取模板
            Template template = templateCache.get(fullTemplateName);
            if (template != null) {
                return template;
            }
            // 从磁盘中加载模板
            synchronized (fullTemplateName.intern()) {
                // 从缓存中获取模板
                template = templateCache.get(fullTemplateName);
                if (template != null) {
                    return template;
                }
                // 从磁盘中加载模板
                template = configuration.getTemplate(fullTemplateName);
                // 放入缓存中
                templateCache.put(fullTemplateName, template);
                return template;
            }
        } catch (IOException e) {
            throw new TurboTemplateRenderException("模板加载失败", e);
        }
    }

    @Override
    public void setMainClass(Class<?> mainClass) {
        this.mainClass = mainClass;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public void setTemplateSuffix(String templateSuffix) {
        this.templateSuffix = templateSuffix;
    }

    public void setOpenCache(boolean openCache) {
        this.openCache = openCache;
    }

    @Override
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void init(Middleware chain) {
        this.configuration = loadTemplateConfiguration();
    }

    /**
     * 加载模板的配置
     *
     * @return 模板的configuration
     */
    private Configuration loadTemplateConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        // 创建模板加载器
        TemplateLoader templateLoader = new ClassTemplateLoader(mainClass.getClassLoader(), templatePath);
        configuration.setTemplateLoader(templateLoader);
        configuration.setDefaultEncoding(charset.name());
        return configuration;
    }
}
