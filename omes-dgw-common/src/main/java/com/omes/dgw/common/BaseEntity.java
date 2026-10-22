package com.omes.dgw.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    protected String id;

    @TableField(fill = FieldFill.INSERT)
    protected LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    protected String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected String updateBy;
}
