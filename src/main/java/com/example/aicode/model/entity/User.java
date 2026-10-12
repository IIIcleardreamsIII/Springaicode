package com.example.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.crypto.KeyGenerator;

// ============================================================
// Lombok 与 MyBatis-Flex 注解科普（请先读这里）
// ------------------------------------------------------------
// Lombok 这几个注解在编译期自动生成代码，免写样板：
//   @Data          → getter/setter/toString/equals/hashCode
//   @Builder       → 生成"建造者"模式，可用 User.builder().id(1L).build() 链式构造
//   @NoArgsConstructor  → 无参构造（MyBatis 反射建对象必需）
//   @AllArgsConstructor → 全参构造
//
// MyBatis-Flex 注解（ORM 映射，把 Java 对象 ↔ 数据库行）：
//   @Table("user")  → 本类对应数据库 user 表
//   @Id            → 主键；KeyType.Generator + snowFlakeId 表示插入时由雪花算法自动生成
//   @Column        → 字段与列名映射；isLogicDelete=true 标记该列为逻辑删除列
// ============================================================

/**
 * 用户实体类（Entity / DO：Data Object，与数据库表一一对应）。
 * <p>
 * 对应数据库 user 表，承载账号、密码、昵称、角色、时间等字段。
 * 主键用雪花算法生成，isDelete 实现软删除。实现 Serializable 以便存入 Session。
 *
 * @author <a>程序员yaj</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user") // 映射到数据库 user 表
public class User implements Serializable {

    /** 序列化版本号（用于 Session 等场景的对象序列化兼容） */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 id。
     * @Id 标记为主键；keyType=Generator 表示"插入时由生成器产生"，
     * value=KeyGenerators.snowFlakeId 指定用雪花算法生成全局唯一 Long。
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 登录账号（业务上唯一，注册时校验） */
    @Column("userAccount")
    private String userAccount;

    /** 用户密码（注意：库里存的是加盐 MD5 密文，绝非空明文） */
    @Column("userPassword")
    private String userPassword;

    /** 用户昵称 */
    @Column("userName")
    private String userName;

    /** 用户头像 URL */
    @Column("userAvatar")
    private String userAvatar;

    /** 用户简介 */
    @Column("userProfile")
    private String userProfile;

    /** 用户角色：user / admin（对应 UserRoleEnum 的 value） */
    @Column("userRole")
    private String userRole;

    /** 编辑时间（最后一次修改资料的时间） */
    @Column("editTime")
    private LocalDateTime editTime;

    /** 创建时间（插入时由数据库/框架填充） */
    @Column("createTime")
    private LocalDateTime createTime;

    /** 更新时间（每次更新自动刷新） */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0-未删除，1-已删除。
     * isLogicDelete=true 告诉 MyBatis-Flex：删除操作改为 UPDATE isDelete=1，
     * 且所有查询自动附加 isDelete=0 条件，实现"软删除"（数据不物理消失）。
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
