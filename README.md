# NLKWhitelistX

可追踪添加和移除的 Velocity 白名单插件，支持 正版验证/第三方API自验证 双模式。

## 特性

* 详细的白名单数据，包括担保人、时间、备注、和添加它的操作人员；
* 删除白名单后仍可查询其添加白名单的时间与删除白名单的时间及理由；
* 支持通过 正版验证/第三方API自验证 请求玩家数据；
* 更加贴近原版 Minecraft Server 的 Whitelist 输出；
* 其它基本元数据。

## 变化

* 基于 NLKWhitelist 完全重写；
* 仅支持Velocity；
* 支持使用 正版验证/第三方API自验证 验证UUID并存入数据，更加贴近原版 Minecraft Server 提供的 Whitelist 功能；
* 更优雅的输出和指令系统；
* 对 NLKWhitelist 的屎山进行重写，并堆出了另一个屎山.d

## 命令

### 插件&指令介绍

```
/wl & /wl help
```

### 添加白名单

```
/wl add <玩家ID> <担保人（若无请填 UNKNOWN 或 -）> <审核批次号> <备注>
```

### 移除白名单

```
/wl remove <玩家ID> <删除原因>
```

### 查询白名单

```
/wl query <玩家ID>   - 查询单个玩家的数据
/wl query lot <审核批次号>  - 查询同一批次玩家的数据
/wl query list (-full)  - 查询所有白名单玩家的数据（可添加 -full 选项输出更详细的数据）
/wl query all (-full)  - 查询数据库内所有玩家的数据，包括已被移除白名单的玩家（可添加 -full 选项输出更详细的数据）
```

### 重载插件

```
/wl reload
```

## License
该项目采用 MIT 许可证 - 有关详细信息，请参阅[LICENSE](LICENSE)。

