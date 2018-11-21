package com.mmall.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mmall.dao.SysAclMapper;
import com.mmall.dao.SysAclModuleMapper;
import com.mmall.dao.SysDeptMapper;
import com.mmall.dto.AclDto;
import com.mmall.dto.AclModuleLevelDto;
import com.mmall.dto.DeptLevelDto;
import com.mmall.model.SysAcl;
import com.mmall.model.SysAclModule;
import com.mmall.model.SysDept;
import com.mmall.util.LevelUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysTreeService {

    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysAclModuleMapper sysAclModuleMapper;
    @Resource
    private SysCoreService sysCoreService;
    @Resource
    private SysAclMapper sysAclMapper;

    //通过用户id查询到这个用户具备什么权限
    public List<AclModuleLevelDto> userAclTree(int userId) {
        //通过用户id查询到这个用户具备什么权限
        List<SysAcl> userAclList = sysCoreService.getUserAclList(userId);
        //把权限转为Dto放到树里面去展示出来
        List<AclDto> aclDtoList = Lists.newArrayList();
        for (SysAcl acl : userAclList) {
            AclDto dto = AclDto.adapt(acl);
            dto.setHasAcl(true);
            dto.setChecked(true);
            aclDtoList.add(dto);
        }
        return aclListToTree(aclDtoList);
    }

    //通过角色id获得权限树，传入角色id
    //返回来的是权限模块的DTo类型的list
    public List<AclModuleLevelDto> roleTree(int roleId) {
        // 1、获取当前用户全部可以分配的权限点
        // 这样我们就可以知道哪些权限我们是可以给他分配勾上了，页面显示可以自己需要勾选分配不
        //这个方法我们需要用到的用户id,直接从访问线程里面拿到，不直接传用户id参数
        //实际这个方法主要是为了给超级管理员分配权限的,超级管理可以在这个分配方法这里拿到全部的权限
        //普通用户能拿到的分配权限也是已经分配好了的权限（普通用户能分配的权限和下面第二个方法，查询角色分配的权限点结果基本是一样的）
        List<SysAcl> userAclList = sysCoreService.getCurrentUserAclList();
        // 2、获取当前角色分配的权限点
        //就是本身角色已经勾选的权限点，就是已经具备的权限，页面显示出来是已经打钩的
        List<SysAcl> roleAclList = sysCoreService.getRoleAclList(roleId);
        // 3、获取当前系统所有权限点
        List<AclDto> aclDtoList = Lists.newArrayList();

        //把当前用户可以为角色分配的权限id给他才放到set里面(就是这个用户充当的角色的全部可分配权限）
        Set<Integer> userAclIdSet = userAclList.stream().map(sysAcl -> sysAcl.getId()).collect(Collectors.toSet());
        //把当前角色已经分配的权限id给他放到set里面(就是这个角色已分配权限）
        Set<Integer> roleAclIdSet = roleAclList.stream().map(sysAcl -> sysAcl.getId()).collect(Collectors.toSet());
        //查询系统全部的权限
        List<SysAcl> allAclList = sysAclMapper.getAll();
        //遍历系统全部权限，转为dto
        for (SysAcl acl : allAclList) {
            AclDto dto = AclDto.adapt(acl);

            //假如当前的角色已经分配的权限id出现在全部权限的id里面,
            //当前这个dto，就是这个当前权限的dto要给他的check属性改为true,这样页面才知道这个角色已经具备这个权限，要给他勾选上
            if (roleAclIdSet.contains(acl.getId())) {
                dto.setChecked(true);
            }
            //假如当前用户可以为角色分配的全部权限id出现在全部权限的id里面
            //说明这个权限是可以被分配给角色的，具备可以操作的属性，让我们自己选择勾选不,所以可操作属性也要改为true(可操作)
            if (userAclIdSet.contains(acl.getId())) {
                dto.setHasAcl(true);
            }

            //最后这个aclDtoList包含的是系统的全部权限在里面,然后有些权限的状态是通过判断用户然后给他进行了勾选和操作的属性
            //然后这个数据就是我们处理好的数据,有了这个数据，我们就可以放到系统树里面去，让他转换出我们要的展示
            aclDtoList.add(dto);
        }

        //然后传入处理好的数据，这个数据就是我们处理好的数据,有了这个数据，我们就可以放到系统树里面去，让他转换出我们要的展示
        //让页面给他展现出来,知道当前操作用户有什么权限可以分配和已经配方的权限
        return aclListToTree(aclDtoList);
    }


    //权限树的展现，把查询处理好的数据传进来
    //然后把处理好的权限数据转化为树，传入的参数就是处理过的用户在系统中的权限数据
    public List<AclModuleLevelDto> aclListToTree(List<AclDto> aclDtoList) {
        //先判断下传过来的权限数据是不是为空,一般不会为空，
        //**这里传的是整个系统的全部权限数据Dto(用户可以具备的权限的Dto里面的勾选和操作属性已经改为true了,后期转为树的时候，这些属性就跟着过去,前台分辨)**
        if (CollectionUtils.isEmpty(aclDtoList)) {
            return Lists.newArrayList();
        }
        //这是单独生成的权限树,调用后面的aclModuleTree()方法,得到一个系统的权限模块的列表,这样就拿到系统的全部权限的树
        //目前这个树只是当前权限系统全部的权限模块组成的树，不具备任何其他的操作数据
        //这里把系统的权限模块树给他引入进来了
        List<AclModuleLevelDto> aclModuleLevelList = aclModuleTree();

        //写一个map，key:权限模块id，value:具体的权限
        //这样我们就可以通过这个map,快速知道哪些模块下面有哪些权限了
        //这是我们系统有效的权限点构成的map，状态为1是可用的权限，无限权限不会放进去这个map里面了
        Multimap<Integer, AclDto> moduleIdAclMap = ArrayListMultimap.create();
        //然后遍历一下查询到的用户权限Dto的列表,aclDtoList是已经处理好的系统的全部权限,把对应的权限归类到对应的权限模块下面
        for(AclDto acl : aclDtoList) {
            //先判断一下权限模块列表是不是有效
            if (acl.getStatus() == 1) {
                //有效就从权限里面获取到权限模块的id放进去map里面作为key,然后把对应的权限放到这个key下面作为value
                //这样我们就可以通过这个map,快速知道哪些权限模块里面有哪些权限了
                //整个map放的是整个系统处理好之后的权限的Dto，用户可以操作的权限里面的属性是为true,不可操作的权限属性是为fasle的
                //这里处理好了整个系统属于哪个权限模块的数据Map
                moduleIdAclMap.put(acl.getAclModuleId(), acl);
            }
        }
        //然后就需要把处理好的整个系统权限数据绑定到权限模块树上面
        //传入参数
        //参数1：系统原本生成的权限模块树
        //参数2：处理好的整个系统权限Map(key：权限模块id,value:权限）
        //进入绑定方法，把对应的权限给他放到对应的权限模块的权限list里面
        bindAclsWithOrder(aclModuleLevelList, moduleIdAclMap);
        //上面绑定完了之后，返回去我们已经绑定过的系统树就可以了
        return aclModuleLevelList;
    }


    //传入参数就是系统权限模块树(权限没有任何勾选和操作数据),第二个参数是我们从数据库查到了全部的权限的数据（用户角色能操作的数据也在里面了，有操作属性true表示的）
    //**这一步要做的是把我们查到的系统每个权限，给他放到对应的权限模块里面(这些模块里权限列表原来都是空值)，把权限模块里面的权限列表的值给他加上
    public void bindAclsWithOrder(List<AclModuleLevelDto> aclModuleLevelList, Multimap<Integer, AclDto> moduleIdAclMap) {
        //首先要判断权限模块是不是为空，为空直接返回这个模块一个空列表，说明模块下面没有其他权限
        //因为这里我们后面是递归方法，所以需要判断是不是为空
        if (CollectionUtils.isEmpty(aclModuleLevelList)) {
            return;
        }
        //如果不为空就需要遍历当前的层级的权限模块,获取他的每一个模块的列表,dto就是每个模块权限
        for (AclModuleLevelDto dto : aclModuleLevelList) {
            //然后就可以通过权限模块的id,从map里面取出来我们存储的权限的列表,转为list,这样就可以取到当前权限模块下面的全部权限有什么
            //相当拿整个权限树的权限模块id，一个个模块去遍历我们已经处理好的整个系统权限Map，把查到对应的权限数据给他放入权限模块里面权限list
            List<AclDto> aclDtoList = (List<AclDto>)moduleIdAclMap.get(dto.getId());

            //假如权限列表不为空，说明有值，我们就需要给他权限排序
            if (CollectionUtils.isNotEmpty(aclDtoList)) {
                //权限排序
                Collections.sort(aclDtoList, aclSeqComparator);
                //这个dto就是我们系统的权限模块树，假如能查到权限值，把查到对应的权限数据给他放入权限模块里面权限list里面
                //样才能看到哪些权限是可以勾选和被勾选的状态
                //查不到的说明这个用户不具备这些权限，就不会进来这里修改了
                dto.setAclList(aclDtoList);
            }

            //然后再遍历这个权限模块里面的权限模块属性，看看有没有子模块，进入遍历
            //然后就靠这样首层模块一个个模块遍历，有权限值就放进去，有子模块就进入第二次遍历，然后把整个权限树都遍历好，填充好对应的权限数据，然后这个树就完成了
            bindAclsWithOrder(dto.getAclModuleList(), moduleIdAclMap);
        }
    }


    //第一步先准备好数据
    //权限模块树，权限树是由权限模块来组成的，所以先查询整个系统的权限模块列表
    //这个方法主要是获得整个系统的权限模块
    public List<AclModuleLevelDto> aclModuleTree() {
        //首先取出当前系统全部权限模块的list
        List<SysAclModule> aclModuleList = sysAclModuleMapper.getAllAclModule();
        //new一个权限模块Dto的list来把我们查到的系统数据转为我们需要展现的数据Dto结构
        List<AclModuleLevelDto> dtoList = Lists.newArrayList();
        //把从数据库取到权限模块数据，通过遍历把他们全部转化为Dto对象,放到list里面
        for (SysAclModule aclModule : aclModuleList) {
            //通过静态转换方法，直接把数据库的数据，转为我们要的AclModuleLevelDto对象属性,然后放到dtoList里面
            dtoList.add(AclModuleLevelDto.adapt(aclModule));
        }
        //把权限模块Dto对象传给递归树转换方法,把全部模块做成树结构就靠下面这个方法了
        return aclModuleListToTree(dtoList);
    }


    //第二步把权限模块排序和给权限模块归类Map，准备好这些数据，在第三步才能转换成树
    //假如有父级权限模块，还需要将模块归类到map里面
    //把权限模块Dto对象传给递归树转换方法,把全部模块做成树结构就靠下面这个方法了
    //dtoList就是系统的权限模块的Dto
    public List<AclModuleLevelDto> aclModuleListToTree(List<AclModuleLevelDto> dtoList) {
        //先判断权限模块层级对象是不是空值，如果为空返回一个空的list
        //用list的数据之前我们最好都是先判断是不是为空，防止出现空指针的情况
        if (CollectionUtils.isEmpty(dtoList)) {
            return Lists.newArrayList();
        }
        //组装一个map结构，这个结构的组成是，key:模块层级，value:该层级下面的所有权限模块
        //这个Map结构，我们就可以通过模块层级，取出属于这个层级的所有权限模块
        //比如0级，就是顶级层级，然后有几个权限模块层级都是0级，所以就把这几个权限模块都放到0级下面
        //所以一个key，下面放的是一个list的列表（多个权限模块组成）
        // level -> [aclmodule1, aclmodule2, ...] Map<String, List<Object>>
        //把同一个层级的权限模块给他放到一个map里面，这样就可以通过key来获取到这个层级下面的全部权限模块
        //new一个Map对象
        Multimap<String, AclModuleLevelDto> levelAclModuleMap = ArrayListMultimap.create();
        //new一个List，后面用来放首层的权限模块用的
        List<AclModuleLevelDto> rootList = Lists.newArrayList();
        //开始遍历，把数据库传过来的权限模块全部遍历一次,这样组装成我们要的 levelAclModuleMap，和顺便组合出首层模块List
        for (AclModuleLevelDto dto : dtoList) {
            //把权限层级（level）相同的权限模块，给他放到一个map里面,方便我们后面取数据
            levelAclModuleMap.put(dto.getLevel(), dto);
            //假如是原始层级，就把他放到rootList里面
            //这样我们就会得到首层的模块,判断下层级是不是为0，是0就是顶层模块
            if (LevelUtil.ROOT.equals(dto.getLevel())) {
                rootList.add(dto);
            }
        }
        //先将首层的模块进行一次排序
        Collections.sort(rootList, aclModuleSeqComparator);
        //排完序之后，调用递归方法，传入首层模块，开始遍历

        //传入首层权限模块展现成树，第一次是从首层开始递归下去，所以从传入是的层级0
        //传入的参数，是首层的权限列表，等级列表，全部等级下的全部权限
        transformAclModuleTree(rootList, LevelUtil.ROOT, levelAclModuleMap);

        //上面模块递归好了，每个首层模块里面的子模块的值都有了，然后我们就可以直接返回我们首层的权限模块List数据了
        return rootList;
    }

    //第三步递归成树
    //将传入进来的首层权限模块list,遍历出每个权限模块下面的子模块和子权限
    //参数，当前层级权限模块列表List，当前权限等级，全部等级的权限模块map（方便我们取数据）
    public void transformAclModuleTree(List<AclModuleLevelDto> dtoList, String level, Multimap<String, AclModuleLevelDto> levelAclModuleMap) {
        //开始遍历,dtoList就是我们前面排好序的权限模块的List
        for (int i = 0; i < dtoList.size(); i++) {

            //开始遍历里面元素，遍历当前层级的每个元素,首先把权限模块点对象从list里面拿到，转为dto对象才能通过对象进行操作
            //现在就相当拿到了一个权限模块点对象了，就是dto
            //每个dto对象里面有自己的权限模块点list和权限的list属性的，
            //所以先把首层每个权限模块都变成dto对象类,才能对对象进行后续的操作
            AclModuleLevelDto dto = dtoList.get(i);

            //子级权限模块层级等级的形成是由：父级的层级+父级的id
            //除了顶层层级的权限模块层级为0
            //所以这里计算该权限模块点的子权限模块层级是什么,然后才能去map里面查找这个层级有没有对应权限模块，才知道这个dto对象（这个权限模块）里有无子权限模块
            String nextLevel = LevelUtil.calculateLevel(level, dto.getId());
            //下一个层级的列表,从系统权限模块数据map里面得到，然后把Map数据强制转为list,这样就得到下一个层级的权限模块了
            //传入权限子层级key，查询获取到该子层级里面的全部权限模块，放到list里面，为了看看他的子等级还有没有权限
            //这样查找该权限模块下的子权限模块数据，放到tempList里
            List<AclModuleLevelDto> tempList = (List<AclModuleLevelDto>) levelAclModuleMap.get(nextLevel);

            //假如tempList为空，说明这个模块没有子权限模块了，所以就算遍历好了这个权限模块，不会继续往下走进入if里面，就会回到前面的for循环，遍历第二个元素
            //假如子权限模块List为空，那么我们就不需要进行任何操作了，那么这个dto属性里面的权限模块列表和权限点就为空了
            // 假如发现这个tempList不为空，说明这个权限模块下面还有子权限模块
            if (CollectionUtils.isNotEmpty(tempList)) {
                //子权限列表同样进行一次排序
                Collections.sort(tempList, aclModuleSeqComparator);

                //子权限查询到了放进去了tempList，那么我们就要把tempList放到dto对象（当前权限模块）里面的AclModuleList属性里面，这个属性就是放子权限模块列表的
                //那么当前这个权限列表里面的子权限列表属性就有值了,(tempList就是这个模块里面的子权限列表)
                dto.setAclModuleList(tempList);
                //把我们新查的子权限模块list给他传进去，再次调用递归树，再次一个个权限遍历，然后再次看有没有子权限模块的子权限模块，不断递归。
                //参数：下一层级权限，下一层级等级，全部权限模块数据map
                transformAclModuleTree(tempList, nextLevel, levelAclModuleMap);
            }
        }
    }

    public List<DeptLevelDto> deptTree() {
        List<SysDept> deptList = sysDeptMapper.getAllDept();

        List<DeptLevelDto> dtoList = Lists.newArrayList();
        for (SysDept dept : deptList) {
            DeptLevelDto dto = DeptLevelDto.adapt(dept);
            dtoList.add(dto);
        }
        return deptListToTree(dtoList);
    }

    public List<DeptLevelDto> deptListToTree(List<DeptLevelDto> deptLevelList) {
        if (CollectionUtils.isEmpty(deptLevelList)) {
            return Lists.newArrayList();
        }
        // level -> [dept1, dept2, ...] Map<String, List<Object>>
        Multimap<String, DeptLevelDto> levelDeptMap = ArrayListMultimap.create();
        List<DeptLevelDto> rootList = Lists.newArrayList();

        for (DeptLevelDto dto : deptLevelList) {
            levelDeptMap.put(dto.getLevel(), dto);
            if (LevelUtil.ROOT.equals(dto.getLevel())) {
                rootList.add(dto);
            }
        }

        // 按照seq从小到大排序
        Collections.sort(rootList, new Comparator<DeptLevelDto>() {
            public int compare(DeptLevelDto o1, DeptLevelDto o2) {
                return o1.getSeq() - o2.getSeq();
            }
        });
        // 递归生成树
        transformDeptTree(rootList, LevelUtil.ROOT, levelDeptMap);
        return rootList;
    }


    // level:0, 0, all 0->0.1,0.2
    // level:0.1
    // level:0.2
    public void transformDeptTree(List<DeptLevelDto> deptLevelList, String level, Multimap<String, DeptLevelDto> levelDeptMap) {
        for (int i = 0; i < deptLevelList.size(); i++) {
            // 遍历该层的每个元素
            DeptLevelDto deptLevelDto = deptLevelList.get(i);
            // 处理当前层级的数据
            String nextLevel = LevelUtil.calculateLevel(level, deptLevelDto.getId());
            // 处理下一层
            List<DeptLevelDto> tempDeptList = (List<DeptLevelDto>) levelDeptMap.get(nextLevel);
            if (CollectionUtils.isNotEmpty(tempDeptList)) {
                // 排序
                Collections.sort(tempDeptList, deptSeqComparator);
                // 设置下一层部门
                deptLevelDto.setDeptList(tempDeptList);
                // 进入到下一层处理
                transformDeptTree(tempDeptList, nextLevel, levelDeptMap);
            }
        }
    }

    public Comparator<DeptLevelDto> deptSeqComparator = new Comparator<DeptLevelDto>() {
        public int compare(DeptLevelDto o1, DeptLevelDto o2) {
            return o1.getSeq() - o2.getSeq();
        }
    };

    public Comparator<AclModuleLevelDto> aclModuleSeqComparator = new Comparator<AclModuleLevelDto>() {
        public int compare(AclModuleLevelDto o1, AclModuleLevelDto o2) {
            return o1.getSeq() - o2.getSeq();
        }
    };

    public Comparator<AclDto> aclSeqComparator = new Comparator<AclDto>() {
        public int compare(AclDto o1, AclDto o2) {
            return o1.getSeq() - o2.getSeq();
        }
    };
}

