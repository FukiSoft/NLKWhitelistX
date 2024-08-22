package com.leitingsd.plugins.nlkwhitelistx.manager;

import java.sql.Timestamp;

public class WhitelistRecord {
    private long id;
    private String uuid;
    private String player;
    private String oldid;      // 新增 oldid 字段
    private String operator;
    private String guarantor;
    private String lotnumber;
    private String description;
    private Timestamp time;
    private long deleteAt;
    private String deleteOperator;
    private String deleteReason;

    public WhitelistRecord(long id, String uuid, String player, String oldid, String operator, String guarantor, String lotnumber, String description, Timestamp time, long deleteAt, String deleteOperator, String deleteReason) {
        this.id = id;
        this.uuid = uuid;
        this.player = player;
        this.oldid = oldid;   // 初始化 oldid 字段
        this.operator = operator;
        this.guarantor = guarantor;
        this.lotnumber = lotnumber;
        this.description = description;
        this.time = time;
        this.deleteAt = deleteAt;
        this.deleteOperator = deleteOperator;
        this.deleteReason = deleteReason;
    }

    // Getter methods for all fields

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public String getPlayer() {
        return player;
    }

    public String getOldid() {
        return oldid;
    }

    public String getOperator() {
        return operator;
    }

    public String getGuarantor() {
        return guarantor;
    }

    public String getLotnumber() {
        return lotnumber;
    }

    public String getDescription() {
        return description;
    }

    public Timestamp getTime() {
        return time;
    }

    public long getDeleteAt() {
        return deleteAt;
    }

    public String getDeleteOperator() {
        return deleteOperator;
    }

    public String getDeleteReason() {
        return deleteReason;
    }
}
