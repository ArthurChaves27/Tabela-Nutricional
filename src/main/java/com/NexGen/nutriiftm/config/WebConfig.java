package com.NexGen.nutriiftm.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
/**
 * Registra o AuthInterceptor.
 *
 * O painel administrativo exige conta com papel de administrador. O
 * perfil, sua edição e as ações sociais exigem sessão. Os demais recursos
 * seguem públicos para produtores/cooperativas usarem sem conta.
 *
 * IMPORTANTE: sempre que você criar uma página nova que deva exigir
 * login, adicione o padrão dela em addPathPatterns; por padrão, tudo que
 * não está listado aqui fica público.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${app.uploads.dir:./uploads}")
    private String uploadsDir;  

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CsrfInterceptor csrfInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/admin",
                        "/admin/**",
                        "/perfil",
                        "/perfil/**"
                );
        registry.addInterceptor(csrfInterceptor)
                .addPathPatterns("/perfil")
                .addPathPatterns("/perfil/**");
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String raiz = Path.of(uploadsDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + raiz + "/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
