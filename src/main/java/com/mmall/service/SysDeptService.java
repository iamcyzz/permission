package com.mmall.service;

import com.google.common.base.Preconditions;
import com.mmall.common.RequestHolder;
import com.mmall.dao.SysDeptMapper;
import com.mmall.dao.SysUserMapper;
import com.mmall.exception.ParamException;
import com.mmall.model.SysDept;
import com.mmall.param.DeptParam;
import com.mmall.util.BeanValidator;
import com.mmall.util.IpUtil;
import com.mmall.util.LevelUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class SysDeptService {

    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysLogService sysLogService;

    public void save(DeptParam param) {
        BeanValidator.check(param);
        if(checkExist(param.getParentId(), param.getName(), param.getId())) {
            throw new ParamException("同一层级下存在相同名称的部门");
        }
        SysDept dept = SysDept.builder().name(param.getName()).parentId(param.getParentId())
                .seq(param.getSeq()).remark(param.getRemark()).build();

        //设置里面的等级,通过工具类计算出等级，传入参数为父级id
        dept.setLevel(LevelUtil.calculateLevel(getLevel(param.getParentId()), param.getParentId()));
        dept.setOperator(RequestHolder.getCurrentUser().getUsername());
        dept.setOperateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest()));
        dept.setOperateTime(new Date());
        sysDeptMapper.insertSelective(dept);
        sysLogService.saveDeptLog(null, dept);
    }

    public void update(DeptParam param) {
        BeanValidator.check(param);
        if(checkExist(param.getParentId(), param.getName(), param.getId())) {
            throw new ParamException("同一层级下存在相同名称的部门");
        }
        SysDept before = sysDeptMapper.selectByPrimaryKey(param.getId());
        Preconditions.checkNotNull(before, "待更新的部门不存在");
        if(checkExist(param.getParentId(), param.getName(), param.getId())) {
            throw new ParamException("同一层级下存在相同名称的部门");
        }

        SysDept after = SysDept.builder().id(param.getId()).name(param.getName()).parentId(param.getParentId())
                .seq(param.getSeq()).remark(param.getRemark()).build();
        after.setLevel(LevelUtil.calculateLevel(getLevel(param.getParentId()), param.getParentId()));
        after.setOperator(RequestHolder.getCurrentUser().getUsername());
        after.setOperateIp(IpUtil.getRemoteIp(RequestHolder.getCurrentRequest()));
        after.setOperateTime(new Date());

        updateWithChild(before, after);
       // sysLogService.saveDeptLog(before, after);
    }

    @Transactional
    public void updateWithChild(SysDept before, SysDept after) {

        //子级的等级id是由父级的等级+父级id组合成的。
        //所以当子级的当前等级发生变化的时候，他会影响到的就是他的子部门的等级

        //查询部门更新前等级
        String oldLevelPrefix = before.getLevel();
        //查询部门更新后等级
        String newLevelPrefix = after.getLevel();

        //假如部门更新了等级，前后等级不一样了，那么就需要更改部门的子部门等级
        if (!after.getLevel().equals(before.getLevel())) {
            //查询部门的子部门
            List<SysDept> deptList = sysDeptMapper.getChildDeptListByLevel(before.getLevel());
            //判断子部门列表是不是为空
            if (CollectionUtils.isNotEmpty(deptList)) {
                //遍历
                for (SysDept dept : deptList) {
                    //取出子部门的等级
                    String level = dept.getLevel();
                    //再判断下，等级假如以父级部门的等级前置的话，那么我们才进行处理，不过一般都是符合的了
                    if (level.indexOf(oldLevelPrefix) == 0) {
                        //计算出新的等级
                        //父部门新等级+去掉了父部门旧等级前缀后的字符串,拼接成为新的子部门等级
                        level = newLevelPrefix + level.substring(oldLevelPrefix.length());
                        dept.setLevel(level);
                    }
                }
                //调用我们写的批量更新的方法，传入部门的列表去更新
                sysDeptMapper.batchUpdateLevel(deptList);
            }
        }
        sysDeptMapper.updateByPrimaryKey(after);
    }

    //查看部门是不是存在
    private boolean checkExist(Integer parentId, String name, Integer deptId) {
        return sysDeptMapper.countByNameAndParentId(parentId, name, deptId) > 0;
    }
    //通过id来获取等级
    private String getLevel(Integer deptId) {
        SysDept dept = sysDeptMapper.selectByPrimaryKey(deptId);
        if (dept == null) {
            return null;
        }
        return dept.getLevel();
    }


    public void delete(int deptId) {
        SysDept dept = sysDeptMapper.selectByPrimaryKey(deptId);
        Preconditions.checkNotNull(dept, "待删除的部门不存在，无法删除");
        if (sysDeptMapper.countByParentId(dept.getId()) > 0) {
            throw new ParamException("当前部门下面有子部门，无法删除");
        }
//        if(sysUserMapper.countByDeptId(dept.getId()) > 0) {
//            throw new ParamException("当前部门下面有用户，无法删除");
//        }
        sysDeptMapper.deleteByPrimaryKey(deptId);
    }
}
