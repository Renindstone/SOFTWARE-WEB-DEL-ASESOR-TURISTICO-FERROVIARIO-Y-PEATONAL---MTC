package com.turismo.config;

import com.turismo.security.AccesoDenegadoHandler;
import com.turismo.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad por roles (HURF06 / RNF-05): ADMIN_MTC, TRAVEL_GROUP_USER,
 * PERURAIL_ADMIN, TURISTA_PUBLICO.
 *
 * El panel del turista (preferencias, zonas, ruta e informe) es publico: el
 * caso "Zonas turisticas" del MTC contempla la consulta anonima, y por eso
 * InfIdUsuario admite NULL en el diccionario de datos (6.4). El modulo de
 * administracion queda restringido: CRUD de zonas y consulta de estaciones
 * para TRAVEL_GROUP_USER, horarios y tarifas (RF-12) para PERURAIL_ADMIN y
 * auditoria (RF-15) para ADMIN_MTC; ADMIN_MTC entra a los tres.
 */
@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final AccesoDenegadoHandler accesoDenegadoHandler;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                           AccesoDenegadoHandler accesoDenegadoHandler) {
        this.customUserDetailsService = customUserDetailsService;
        this.accesoDenegadoHandler = accesoDenegadoHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(customUserDetailsService)
                .authorizeHttpRequests(auth -> auth
                        // Recursos estaticos y pantallas de acceso.
                        // /registro es publico por necesidad: quien se da de alta
                        // todavia no tiene sesion. Solo concede TURISTA_PUBLICO,
                        // porque el rol lo fija UsuarioService y no la peticion.
                        .requestMatchers("/", "/login", "/registro", "/acceso-denegado", "/error",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        // Sonda de vida del contenedor (healthcheck de docker-compose).
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Mocks de los servicios externos (Sprint 0). SenamhiClient los
                        // consume por HTTP como si fueran el servicio real, sin credenciales.
                        .requestMatchers("/mock/**").permitAll()
                        // Modulo cliente / usuario final (RF-01 a RF-08, CU-01 a CU-03, CU-08).
                        .requestMatchers("/preferencias/**", "/estaciones/seleccion",
                                "/rutas/**", "/informes/**").permitAll()
                        // RNF-05: el historial es del turista (y del MTC); los operadores
                        // externos no planifican visitas. El menu ya lo oculta para ellos.
                        .requestMatchers("/mis-informes").hasAnyRole("TURISTA_PUBLICO", "ADMIN_MTC")
                        // Modulo de administracion (RNF-05).
                        .requestMatchers("/auditoria/**").hasRole("ADMIN_MTC")
                        .requestMatchers("/servicios-tren/**").hasAnyRole("ADMIN_MTC", "PERURAIL_ADMIN")
                        .requestMatchers("/zonas/**").hasAnyRole("ADMIN_MTC", "TRAVEL_GROUP_USER")
                        .requestMatchers("/estaciones/**").hasAnyRole("ADMIN_MTC", "TRAVEL_GROUP_USER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        // POST /logout con token CSRF: el enlace "Salir" del nav es un formulario.
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(handling -> handling.accessDeniedHandler(accesoDenegadoHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }
}
