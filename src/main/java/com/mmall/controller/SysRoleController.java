package com.mmall.controller;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mmall.common.JsonData;
import com.mmall.model.SysRole;
import com.mmall.model.SysUser;
import com.mmall.param.RoleParam;
import com.mmall.service.SysRoleAclService;
import com.mmall.service.SysRoleService;
import com.mmall.service.SysRoleUserService;
import com.mmall.service.SysTreeService;
import com.mmall.service.SysUserService;
import com.mmall.util.StringUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/sys/role")
public class SysRoleController {

    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysTreeService sysTreeService;
    @Resource
    private SysRoleAclService sysRoleAclService;
    @Resource
    private SysRoleUserService sysRoleUserService;
    @Resource
    private SysUserService sysUserService;

    @RequestMapping("role.page")
    public ModelAndView page() {
        return new ModelAndView("role");
    }
    //保存角色
    @RequestMapping("/save.json")
    @ResponseBody
    public JsonData saveRole(RoleParam param) {
        sysRoleService.save(param);
        return JsonData.success();
    }

    //更新角色
    @RequestMapping("/update.json")
    @ResponseBody
    public JsonData updateRole(RoleParam param) {
        sysRoleService.update(param);
        return JsonData.success();
    }

    //获得所有角色
    @RequestMapping("/list.json")
    @ResponseBody
    public JsonData list() {
        return JsonData.success(sysRoleService.getAll());
    }

    //获取角色权限树
    @RequestMapping("/roleTree.json")
    @ResponseBody
    public JsonData roleTree(@RequestParam("roleId") int roleId) {
        return JsonData.success(sysTreeService.roleTree(roleId));
    }

    //修改用户与角色权限关系
    @RequestMapping("/changeAcls.json")
    @ResponseBody
    //传入权限的id
    public JsonData changeAcls(@RequestParam("roleId") int roleId, @RequestParam(value = "aclIds", required = false, defaultValue = "") String aclIds) {
        //把转入的权限id数据转为list的结构
        List<Integer> aclIdList = StringUtil.splitToListInt(aclIds);
        //把权限放到对应的角色里面
        sysRoleAclService.changeRoleAcls(roleId, aclIdList);
        return JsonData.success();
    }


    //修改用户与对应的权限关系
    @RequestMapping("/changeUsers.json")
    @ResponseBody
    //参数：角色id，用户id
    public JsonData changeUsers(@RequestParam("roleId") int roleId, @RequestParam(value = "userIds", required = false, defaultValue = "") String userIds) {
        //把传入进来的用户id转为一个list
        List<Integer> userIdList = StringUtil.splitToListInt(userIds);
        //进行角色与用户的保存
        sysRoleUserService.changeRoleUsers(roleId, userIdList);
        return JsonData.success();
    }



    //获取用户角色列表，选角色，就可以看到哪些用户具备不具备这个角色，可以给这个用户分配角色
    @RequestMapping("/users.json")
    @ResponseBody
    public JsonData users(@RequestParam("roleId") int roleId) {
        //传入角色id，看看这个角色已经有了哪些用户
        List<SysUser> selectedUserList = sysRoleUserService.getListByRoleId(roleId);

        //获取所有用户列表
        List<SysUser> allUserList = sysUserService.getAll();
        //未选用户列表
        List<SysUser> unselectedUserList = Lists.newArrayList();

        //把已选的用户List给他放进去set里面,list转为set
        Set<Integer> selectedUserIdSet = selectedUserList.stream().map(sysUser -> sysUser.getId()).collect(Collectors.toSet());
        //然后遍历系统全部用，把状态正常的和不在已选用户里面的用户给他放到未选用户的set集合里面去
        for(SysUser sysUser : allUserList) {
            if (sysUser.getStatus() == 1 && !selectedUserIdSet.contains(sysUser.getId())) {
                unselectedUserList.add(sysUser);
            }
        }

        //把已选用户的的状态不正常的过滤掉，剩下正常的用户放到已选用户列表里，返回回去
        selectedUserList = selectedUserList.stream().filter(sysUser -> sysUser.getStatus() != 1).collect(Collectors.toList());
        Map<String, List<SysUser>> map = Maps.newHashMap();

        //把数据放进去map，返回回去
        map.put("selected", selectedUserList);
        map.put("unselected", unselectedUserList);
        return JsonData.success(map);
    }
}
