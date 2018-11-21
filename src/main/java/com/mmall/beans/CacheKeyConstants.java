package com.mmall.beans;

import lombok.Getter;

@Getter
public enum CacheKeyConstants {

    //系统级别的权限，不需要任何参数，直接用一个常量来放，所有人的权限都是一样的
    //而用户权限，就需要绑定上用户id进去

    //系统的权限
    SYSTEM_ACLS,
    //用户的权限
    USER_ACLS;

}
