package com.gscrm.config;

import com.gscrm.tenant.TenantContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class ShowcaseModelAdvice {

    @ModelAttribute
    public void addShowcase(Model model) {
        model.addAttribute("showcase", TenantContext.isShowcase());
    }
}
