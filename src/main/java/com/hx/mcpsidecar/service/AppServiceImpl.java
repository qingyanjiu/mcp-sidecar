package com.hx.mcpsidecar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hx.mcpsidecar.mapper.AppMapper;
import com.hx.mcpsidecar.model.App;
import org.springframework.stereotype.Service;

/**
 * dify app service
 */
//@Service
public class AppServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<AppMapper, App>
    implements AppService, IService<App> {
}
