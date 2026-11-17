package com.proyecto.PlayApp.Controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.proyecto.PlayApp.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ControllerAdvice
public class ExceptionsController {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionsController.class);
    public static final String DEFAULT_ERROR_VIEW = "errorviews/internal-error";
    private final UsuarioService servicio;

    @ExceptionHandler(value = Exception.class)
    public ModelAndView defaultErrorHandler(
            HttpServletRequest req,
            Exception e,
            Principal principal
    ) {
        logger.error("Error no controlado al procesar petición en [{}]: {}", req.getRequestURL(), e.getMessage(), e);
        ModelAndView view = new ModelAndView();

        view.addObject("url", req.getRequestURL());
        String link = "/shop";
        try {
            if (servicio != null) {
                link = servicio.getHomePath(principal);
            }
        } catch (Exception ignored) {
        }
        view.addObject("link", link);

        view.setViewName(DEFAULT_ERROR_VIEW);
        return view;
    }

    @ExceptionHandler(value = NoResourceFoundException.class)
    public ModelAndView noResourceFoundErrorHandler(
            HttpServletRequest req,
            Exception e,
            Principal principal
    ) {
        logger.warn("Recurso estático o ruta no encontrada [{}]: {}", req.getRequestURL(), e.getMessage());
        ModelAndView view = new ModelAndView();

        view.addObject("url", req.getRequestURL());
        String link = "/shop";
        try {
            if (servicio != null) {
                link = servicio.getHomePath(principal);
            }
        } catch (Exception ignored) {
        }
        view.addObject("link", link);

        view.setViewName("errorviews/not-found");
        return view;
    }
}
