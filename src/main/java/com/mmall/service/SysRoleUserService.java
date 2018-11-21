package com.mmall.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mmall.beans.LogType;
import com.mmall.common.RequestHolder;
import com.mmall.dao.SysLogMapper;
import com.mmall.dao.SysRoleUserMapper;
import com.mmall.dao.SysUserMapper;
import com.mmall.model.SysLogWithBLOBs;
import com.mmall.model.SysRoleUser;
import com.mmall.model.SysUser;
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
public class SysRoleUserService {

    @Resource
    private SysRoleUserMapper sysRoleUserMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysLogMapper sysLogMapper;


    //通过角色id查到有哪些用户
    public List<SysUser> getListByRoleId(int roleId) {
        //先从角色用户表查到有哪些用户的id的list
        List<Integer> userIdList = sysRoleUserMapper.getUserIdListByRoleId(roleId);
        if (CollectionUtils.isEmpty(userIdList)) {
            return Lists.newArrayList();
        }
        //通过用户id获取到具体用户信息
        return sysUserMapper.getByIdList(userIdList);
    }

    //传入角色id，和用户id
    public void changeRoleUsers(int roleId, List<Integer> userIdList) {
        //查看这个角色本来有用的用户id
        List<Integer> originUserIdList = sysRoleUserMapper.getUserIdListByRoleId(roleId);
        //假如这里list和原来的长度相同，那么他可能什么都没改，需要进去判断下是不是改了用户
        if (originUserIdList.size() == userIdList.size()) {
            //原来用户的id
            Set<Integer> originUserIdSet = Sets.newHashSet(originUserIdList);
            //传入进来的用户id
            Set<Integer> userIdSet = Sets.newHashSet(userIdList);
            //原来的用户id移除掉传入进来的用户id
            originUserIdSet.removeAll(userIdSet);
            //判断移除后是不是为空的列表，为空说明用户和角色的关系没有变化，不进行任何操作，直接返回
            if (CollectionUtils.isEmpty(originUserIdSet)) {
                return;
            }
        }
        //否则进入这里，进行更新
        updateRoleUsers(roleId, userIdList);
        //最后更新操作记录到日志
        saveRoleUserLog(roleId, originUserIdList, userIdList);
    }
    //调用事务方法，要更新就全部要更新成功
    @Transactional
    public void updateRoleUsers(int roleId, List<Integer> userIdList) {
        //先把这个这角色原来有的用户关系全部删除了
        sysRoleUserMapper.deleteByRoleId(roleId);

        //然后判断传入进来的是不是为空的用户的idList,说明没有用户分配了这个角色
        if (CollectionUtils.isEmpty(userIdList)) {
            return;
        }

        //用户角色关系的list，用来放我们新的对象
        List<SysRoleUser> roleUserList = Lists.newArrayList();
        for (Integer userId : userIdList) {
            SysRoleUser roleUser = SysRoleUser.builder().roleId(roleId).userId(userId).operator(RequestHolder.getCurrentUser().getUsername())
                    .operateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest())).operateTime(new Date()).build();
            roleUserList.add(roleUser);
        }
        //把我们新的用户与角色关系，对象通过批量插入，给他放到数据库里面去
        sysRoleUserMapper.batchInsert(roleUserList);
    }

    //操作记录方法
    private void saveRoleUserLog(int roleId, List<Integer> before, List<Integer> after) {
        SysLogWithBLOBs sysLog = new SysLogWithBLOBs();
        sysLog.setType(LogType.TYPE_ROLE_USER);
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
