package cn.lili.modules.permission.serviceimpl;

import cn.lili.modules.permission.entity.dos.RoleMenu;
import cn.lili.modules.permission.entity.vo.UserMenuVO;
import cn.lili.modules.permission.mapper.MenuMapper;
import cn.lili.modules.permission.mapper.RoleMenuMapper;
import cn.lili.modules.permission.service.RoleMenuService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import groovy.util.logging.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 角色菜单业务层实现
 *
 * @author Chopper
 * @date 2020/11/22 11:43
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {

    /**
     * 菜单
     */
    @Resource
    private MenuMapper menuMapper;

    @Override
    public List<RoleMenu> findByRoleId(String roleId) {
        QueryWrapper<RoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_id", roleId);
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<UserMenuVO> findAllMenu(String userId) {
        return menuMapper.getUserRoleMenu(userId);
    }


    @Override
    public void updateRoleMenu(String roleId, List<RoleMenu> roleMenus) {
        try {
            //删除角色已经绑定的菜单
            this.deleteRoleMenu(roleId);
            //重新保存角色菜单关系
            this.saveBatch(roleMenus);
        } catch (Exception e) {
            log.error("修改用户权限错误",e);
        }
    }

    @Override
    public void deleteRoleMenu(String roleId) {
        //删除
        QueryWrapper<RoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_id", roleId);
        this.remove(queryWrapper);
    }
    @Override
    public void deleteRoleMenu(List<String> roleId) {
        //删除
        QueryWrapper<RoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("role_id", roleId);
        this.remove(queryWrapper);
    }
}