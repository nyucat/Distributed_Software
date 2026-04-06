package com.seckill.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.user.dto.LoginDTO;
import com.seckill.user.dto.RegisterDTO;
import com.seckill.user.entity.User;
import com.seckill.user.mapper.UserMapper;
import com.seckill.user.service.UserService;
import com.seckill.user.utils.JwtUtils;
import com.seckill.user.vo.LoginVO;
import com.seckill.user.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // Find user by username
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = this.getOne(queryWrapper);
        
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        
        // Verify password
        String encryptPassword = DigestUtil.md5Hex(loginDTO.getPassword());
        if (!encryptPassword.equals(user.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }
        
        // Generate token
        String token = jwtUtils.generateToken(user.getUserId(), user.getUsername());
        
        // Create VO
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserInfo(userVO);
        
        return loginVO;
    }

    @Override
    public boolean register(RegisterDTO registerDTO) {
        // Check if username exists
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (this.count(queryWrapper) > 0) {
            throw new RuntimeException("Username already exists");
        }
        
        // Create new user
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(DigestUtil.md5Hex(registerDTO.getPassword()));
        user.setPhone(registerDTO.getPhone());
        user.setCreateTime(LocalDateTime.now());
        
        return this.save(user);
    }

    @Override
    public UserVO getUserInfo(Long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }
}
