package org.example.quickcode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserPasswordUpdateRequest implements Serializable {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
