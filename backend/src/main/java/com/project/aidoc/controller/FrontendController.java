package com.project.aidoc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端控制器，用于处理前端路由
 */
@Controller
public class FrontendController {

    /**
     * 处理根路径请求
     */
    @GetMapping(value = "/")
    public String index() {
        return "forward:/index.html";
    }
}