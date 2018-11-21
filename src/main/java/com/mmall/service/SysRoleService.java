package com.mmall.service;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mmall.common.RequestHolder;
import com.mmall.dao.SysRoleAclMapper;
import com.mmall.dao.SysRoleMapper;
import com.mmall.dao.SysRoleUserMapper;
import com.mmall.dao.SysUserMapper;
import com.mmall.exception.ParamException;
import com.mmall.model.SysRole;
import com.mmall.model.SysUser;
import com.mmall.param.RoleParam;
import com.mmall.util.BeanValidator;
import com.mmall.util.IpUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysRoleService {

    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysRoleUserMapper sysRoleUserMapper;
    @Resource
    private SysRoleAclMapper sysRoleAclMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysLogService sysLogService;

    public void save(RoleParam param) {
        BeanValidator.check(param);
        if (checkExist(param.getName(), param.getId())) {
            throw new ParamException("角色名称已经存在");
        }

        //把参数放到新的对象里面，存到数据库
        SysRole role = SysRole.builder().name(param.getName()).status(param.getStatus()).type(param.getType())
                .remark(param.getRemark()).build();
        role.setOperator(RequestHolder.getCurrentUser().getUsername());
        role.setOperateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest()));
        role.setOperateTime(new Date());
        sysRoleMapper.insertSelective(role);
        sysLogService.saveRoleLog(null, role);
    }

    public void update(RoleParam param) {
        BeanValidator.check(param);
        //先判断名称名字可以更新，不会重复
        if (checkExist(param.getName(), param.getId())) {
            throw new ParamException("角色名称已经存在");
        }
        //再通过id判断要更新的角色存在不
        SysRole before = sysRoleMapper.selectByPrimaryKey(param.getId());
        Preconditions.checkNotNull(before, "待更新的角色不存在");

        //
        SysRole after = SysRole.builder().id(param.getId()).name(param.getName()).status(param.getStatus()).type(param.getType())
                .remark(param.getRemark()).build();
        after.setOperator(RequestHolder.getCurrentUser().getUsername());
        after.setOperateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest()));
        after.setOperateTime(new Date());
      //将更新的对象更新到数据库
        sysRoleMapper.updateByPrimaryKeySelective(after);
        sysLogService.saveRoleLog(before, after);
    }


    //查询角色是不是存在
    //一般新增角色，页面的参数是没有带id传过来的,直接传要增加的角色，看数据库存在不
    //更新角色，才会传id过来
    //传入id=1，角色=管理员。假如数据库已经有了，数据：id=1.角色=管理员，那么查询结果就是0，返回fasle，跳过报错，说明这个角色名称不在数据库可以更新
    //传入id=1，角色=管理员。假如数据库已经有了，数据：id=2.角色=管理员，那么查询结果就是1，返回true，直接报错，说明这个角色名称存在数据库不能更新
    //假如传入
    private boolean checkExist(String name, Integer id) {
        return sysRoleMapper.countByName(name, id) > 0;
    }


    //获取全部角色列表
    public List<SysRole> getAll() {
        return sysRoleMapper.getAll();
    }


    //通过用户id获得角色列表
    public List<SysRole> getRoleListByUserId(int userId) {
        //通过用户id获得角色id的list列表
        List<Integer> roleIdList = sysRoleUserMapper.getRoleIdListByUserId(userId);
        //判断角色id的list是不是为空，为空说明没有角色，返回一个空的角色list回去
        if (CollectionUtils.isEmpty(roleIdList)) {
            return Lists.newArrayList();
        }
         //获取角色的list
        return sysRoleMapper.getByIdList(roleIdList);
    }


    //通过权限id获取到角色的id的list,查到这个权限分给了哪些
    public List<SysRole> getRoleListByAclId(int aclId) {
        List<Integer> roleIdList = sysRoleAclMapper.getRoleIdListByAclId(aclId);
        if (CollectionUtils.isEmpty(roleIdList)) {
            return Lists.newArrayList();
        }
        //通过角色id的list查看有哪些角色
        return sysRoleMapper.getByIdList(roleIdList);
    }

    //通过角色list获取用户list
    public List<SysUser> getUserListByRoleList(List<SysRole> roleList) {
        if (CollectionUtils.isEmpty(roleList)) {
            return Lists.newArrayList();
        }
        //从角色list获取角色的id的list
        List<Integer> roleIdList = roleList.stream().map(role -> role.getId()).collect(Collectors.toList());
        //从角色idlist获取对应用户id
        List<Integer> userIdList = sysRoleUserMapper.getUserIdListByRoleIdList(roleIdList);
        if (CollectionUtils.isEmpty(userIdList)) {
            return Lists.newArrayList();
        }
        //通过用过idlist查到对应的用户
        return sysUserMapper.getByIdList(userIdList);
    }
}
