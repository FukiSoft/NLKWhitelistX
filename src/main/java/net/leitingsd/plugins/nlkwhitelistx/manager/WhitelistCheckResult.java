package net.leitingsd.plugins.nlkwhitelistx.manager;

public enum WhitelistCheckResult {
    ALLOWED,          // 在白名单中 (API正常)
    ALLOWED_OFFLINE,  // 在白名单中 (API异常，但本地校验通过)
    NOT_WHITELISTED,  // 不在白名单中
    API_ERROR         // 无法连接 Mojang Service 或第三方 API，且本地无记录
}
