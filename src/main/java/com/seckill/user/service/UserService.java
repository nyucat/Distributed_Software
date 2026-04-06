package com.seckill.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seckill.user.dto.LoginDTO;
import com.seckill.user.dto.RegisterDTO;
import com.seckill.user.entity.User;
import com.seckill.user.vo.LoginVO;
import com.seckill.user.vo.UserVO;

public interface UserService extends IService<User> {
    
    LoginVO login(LoginDTO loginDTO);
    
    boolean register(RegisterDTO registerDTO);
    
    UserVO getUserInfo(Long id);
}
