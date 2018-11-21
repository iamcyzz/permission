package com.mmall.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mmall.beans.LogType;
import com.mmall.common.RequestHolder;
import com.mmall.dao.SysLogMapper;
import com.mmall.dao.SysRoleAclMapper;
import com.mmall.model.SysLogWithBLOBs;
import com.mmall.model.SysRoleAcl;
import com.mmall.util.IpUtil;
import com.mmall.util.JsonMapper;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class SysRoleAclService {

    @Resource
    private SysRoleAclMapper sysRoleAclMapper;
    @Resource
    private SysLogMapper sysLogMapper;

    public void changeRoleAcls(Integer roleId, List<Integer> aclIdList) {
        //先把这个角色原本的权限id给他全部拿到,放到一个list里面
        List<Integer> originAclIdList = sysRoleAclMapper.getAclIdListByRoleIdList(Lists.newArrayList(roleId));
        //旧的权限list和新的权限list进行对比
        //先看看长度一样不，一样说明可能什么权限都没改变
        if (originAclIdList.size() == aclIdList.size()) {
            //分别把list转为set集合
            Set<Integer> originAclIdSet = Sets.newHashSet(originAclIdList);
            Set<Integer> aclIdSet = Sets.newHashSet(aclIdList);
            //然后把旧的权限set集合里面移除掉新的权限集合set里面的元素
            originAclIdSet.removeAll(aclIdSet);
            //假如为空，说明权限没有变化，不进行任何操作，直接跳出方法返回
            if (CollectionUtils.isEmpty(originAclIdSet)) {
                return;
            }
        }
        //假如长度不一样，我们直接给他更新权限
        updateRoleAcls(roleId, aclIdList);
        //最后更新操作记录到日志
        saveRoleAclLog(roleId, originAclIdList, aclIdList);
    }

    //更新权限要加上事务方法
    @Transactional
    public void updateRoleAcls(int roleId, List<Integer> aclIdList) {
        //首先把这个角色的权限全部删除掉,就是把关联关系表里面属于这个角色的数据都删除了
        sysRoleAclMapper.deleteByRoleId(roleId);

        //然后判断下传入的赋予权限是不是为空，为空说明不给他权限了，那么就是直接返回，数据库也不用进行其他操作了
        if (CollectionUtils.isEmpty(aclIdList)) {
            return;
        }

        //假如不为空，就给他赋予对应的新的权限
        List<SysRoleAcl> roleAclList = Lists.newArrayList();
        //遍历权限id
        for(Integer aclId : aclIdList) {
            //新建权限角色对象，然后遍历放到权限角色的list里面
            SysRoleAcl roleAcl = SysRoleAcl.builder().roleId(roleId).aclId(aclId).operator(RequestHolder.getCurrentUser().getUsername())
                    .operateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest())).operateTime(new Date()).build();
            roleAclList.add(roleAcl);
        }
        //把权限角色对象list批量插入到数据库里
        sysRoleAclMapper.batchInsert(roleAclList);
    }

    //操作记录方法
    private void saveRoleAclLog(int roleId, List<Integer> before, List<Integer> after) {
        SysLogWithBLOBs sysLog = new SysLogWithBLOBs();
        sysLog.setType(LogType.TYPE_ROLE_ACL);
        sysLog.setTargetId(roleId);
        sysLog.setOldValue(before == null ? "" : JsonMapper.obj2String(before));
        sysLog.setNewValue(after == null ? "" : JsonMapper.obj2String(after));
        sysLog.setOperator(RequestHolder.getCurrentUser().getUsername());
        sysLog.setOperateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest()));
        sysLog.setOperateTime(new Date());
        sysLog.setStatus(1);
        sysLogMapper.insertSelective(sysLog);
    }
}
