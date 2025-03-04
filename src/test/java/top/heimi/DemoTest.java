package top.heimi;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * TODO
 */
public class DemoTest {
    public static void main(String[] args) throws IOException {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        // 创建模板加载器
        TemplateLoader templateLoader = new ClassTemplateLoader(DemoTest.class.getClassLoader(), "/templates");
        configuration.setTemplateLoader(templateLoader);
        configuration.setDefaultEncoding("UTF-8");
        Template template = configuration.getTemplate("index.ftl");
        System.out.println(template);
    }
}
