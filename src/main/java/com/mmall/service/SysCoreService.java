package com.mmall.service;

import com.google.common.collect.Lists;
import com.mmall.beans.CacheKeyConstants;
import com.mmall.common.RequestHolder;
import com.mmall.dao.SysAclMapper;
import com.mmall.dao.SysRoleAclMapper;
import com.mmall.dao.SysRoleUserMapper;
import com.mmall.model.SysAcl;
import com.mmall.model.SysUser;
import com.mmall.util.JsonMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.type.TypeReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysCoreService {

    @Resource
    private SysAclMapper sysAclMapper;
    @Resource
    private SysRoleUserMapper sysRoleUserMapper;
    @Resource
    private SysRoleAclMapper sysRoleAclMapper;
    @Resource
    private SysCacheService sysCacheService;

    //获取当前用户的可以分配权限的列表，需要从角色判断，从角色才知道对应具备什么分配权限,这样才能知道什么权限是可以勾选的
    //先看用户是不是超级管理员，是超级管理员，系统的全部权限都是可以给他勾选的,可以分配任何权限给任何角色
    public List<SysAcl> getCurrentUserAclList() {
        //获取当前用户id
        int userId = RequestHolder.getCurrentUser().getId();
        return getUserAclList(userId);
    }

    //获取当前用户指定角色的权限列表,就是这个角色我们已经为他分配的权限
    public List<SysAcl> getRoleAclList(int roleId) {
        List<Integer> aclIdList = sysRoleAclMapper.getAclIdListByRoleIdList(Lists.<Integer>newArrayList(roleId));
        if (CollectionUtils.isEmpty(aclIdList)) {
            return Lists.newArrayList();
        }
        return sysAclMapper.getByIdList(aclIdList);
    }

    //这里传入用户id是为了后续可以通过用户id先判断出这个角色是什么，对应有什么权限
    //如果是用户是超级管理员直接拥有全部权限，全部权限都已经分配给这个超级管理员了，这样才能给没有权限的角色分配权限
    //这样才可以在最原始的时候，或者后续需求，把权限分配出去给需要的角色
    //如果是普通角色就查出这个角色后，再查出这个角色具备的权限
    //这个方法就是通过用户id查到用户全部的权限
    public List<SysAcl> getUserAclList(int userId) {
        //先看用户是不是超级管理员，是超级管理员，系统的全部权限都是可以给他勾选的,可以分配任何权限给任何角色
        //是超级管理员的话，直接返回系统的全部权限到当前用户的权限列表里面,说明这个用户具备的权限都在这里了,全部权限都可以勾选
        if (isSuperAdmin()) {
            //假如是超级管理员，那么这个用户就具备全部的权限的分配权限。
            return sysAclMapper.getAll();
        }

        //假如不是超级管理员就需要从用户得到角色，从角色得到对应的权限，给他返回回去
        //这样才知道当前这个用户具备的权限是什么
        //假如不是超级管理员，先通过用户id查看用户的角色，去数据库查询，在角色用户数据关系表中,获取到这个用户对应的角色id的list
        //获得用户角色idList
        List<Integer> userRoleIdList = sysRoleUserMapper.getRoleIdListByUserId(userId);
        //假如角色id的list为空说明这个用户没有分配角色。就返回一个空的角色list,直接跳出循环，没有任何权限
        if (CollectionUtils.isEmpty(userRoleIdList)) {
            return Lists.newArrayList();
        }
        //假如用户角色id不为空就需要查到这个角色具备的权限，去数据库查询，在角色权限数据关系表查，得到一个权限id的list
        List<Integer> userAclIdList = sysRoleAclMapper.getAclIdListByRoleIdList(userRoleIdList);
        //假如这些角色已经没用权限分配了，那么就是空的了，直接返回空的权限
        if (CollectionUtils.isEmpty(userAclIdList)) {
            return Lists.newArrayList();
        }
        //假如是普通用户，就查到这个用户在数据库中已经具备的权限，也只有这些权限可以给这个普通用户勾选了
        //最后通过权限id的list查到对应的权限
        return sysAclMapper.getByIdList(userAclIdList);
    }

//    public boolean isSuperAdmin() {
//        // 这里是我自己定义了一个假的超级管理员规则，实际中要根据项目进行修改
//        // 可以是配置文件获取，可以指定某个用户，也可以指定某个角色
//        SysUser sysUser = RequestHolder.getCurrentUser();
//        if (sysUser.getMail().contains("admin")) {
//            return true;
//        }
//        return false;
//    }

    //在没用规则的时候我们用下面这个代码，给直接给他返回true，这样就是超级管理员，方便我们测试
    public boolean isSuperAdmin() {
        return true;
    }





    //判断有没有权限访问，返回true认为与权限，false就认为没有权限
    public boolean hasUrlAcl(String url) {

        //先判断是不是超级管理员，是超级管理员直接返回具备访问true
        if (isSuperAdmin()) {
            return true;
        }
        //假如这个访问地址没在权限列表里面找到，说明这个地址不需要权限就能访问到,也直接返回true
        List<SysAcl> aclList = sysAclMapper.getByUrl(url);
        if (CollectionUtils.isEmpty(aclList)) {
            return true;
        }

        //获取当前用户的权限列表
        //没用缓存之前用的是getCurrentUserAclList(),每次都要去数据库查
        //现在改用缓存getCurrentUserAclListFromCache();来放用户权限
        List<SysAcl> userAclList = getCurrentUserAclListFromCache();
        //把当前用户的权限列表的id给他拿到，放入Set里面
        Set<Integer> userAclIdSet = userAclList.stream().map(acl -> acl.getId()).collect(Collectors.toSet());

        //首先这个值放这里默认为false,因为假如下面的符合权限for循环判断一个符合的都没有，这个值还是false，
        boolean hasValidAcl = false;
        // 规则：只要有一个权限点有权限，那么我们就认为有访问权限
        for (SysAcl acl : aclList) {
            // 判断一个用户是否具有某个权限点的访问权限,无效的权限不进行判断,直接遍历下一个元素
            if (acl == null || acl.getStatus() != 1) { // 权限点无效
                continue;
            }
            //假如进来了判断，先把这个值改为true，说明了起码进入这里来判断了。遍历了权限是不是在这个用户里面，
            //假如这个网址可以访问的权限，这个包含这个权限，他就直接返回true，跳出去这个循环，说明有权限访问
            //假如这个用户不包含，他就不会直接返回true，跳不出去这个循环，这时候这个hasValidAcl=true,在135行不会被卡主，135行只是为了完整性，起不到作用
            //所以135行不起作用，只是为了结构好看，然后直接进入最后步骤，返回false
            hasValidAcl = true;
            if (userAclIdSet.contains(acl.getId())) {
                return true;
            }
        }

        //假如访问这个地址的权限虽然不为空，但是实际都是无效的权限了，不需要权限就能访问，上面也从来没进入循环的对比判断直接就continue了，压根没进入130行
        //说明这个权限不需要权限就能方法，那么这里就能直接返回true了
        if (!hasValidAcl) {
            return true;
        }
        return false;
    }


    //redis缓存方法，查询该用户的权限
    public List<SysAcl> getCurrentUserAclListFromCache() {
        //从线程冲取出用户的id
        int userId = RequestHolder.getCurrentUser().getId();
        //从redis里面获取到对应的权限值
        String cacheValue = sysCacheService.getFromCache(CacheKeyConstants.USER_ACLS, String.valueOf(userId));
        //假如缓存的值为空，那么就需要重新去数据库查询这个用户的权限值了
        if (StringUtils.isBlank(cacheValue)) {
            //重新去数据库查询这个用户的权限值
            List<SysAcl> aclList = getCurrentUserAclList();
            if (CollectionUtils.isNotEmpty(aclList)) {
                //权限值不为空，就需要存入到redis缓存里面,把list转为string存到redis,这里单位是秒
                sysCacheService.saveCache(JsonMapper.obj2String(aclList), 600, CacheKeyConstants.USER_ACLS, String.valueOf(userId));
            }
            //然后直接这里跳出循环，把查询到的权限list返回去
            return aclList;
        }
        //假如是从redis里面获取的，需要把string值处理成我们想要的List格式的对象结构
        return JsonMapper.string2Obj(cacheValue, new TypeReference<List<SysAcl>>() {
        });
    }
}
