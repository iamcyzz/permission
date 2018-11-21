package com.mmall.dto;

import com.google.common.collect.Lists;
import com.mmall.model.SysAclModule;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Getter
@Setter
@ToString
public class AclModuleLevelDto extends SysAclModule {

    //权限模块等级列表
    private List<AclModuleLevelDto> aclModuleList = Lists.newArrayList();
    //权限列表,每个权限模块下面需要挂权限点，所以需要展现权限列表属性
    private List<AclDto> aclList = Lists.newArrayList();


    public static AclModuleLevelDto adapt(SysAclModule aclModule) {
        AclModuleLevelDto dto = new AclModuleLevelDto();
        BeanUtils.copyProperties(aclModule, dto);
        return dto;
    }
}
