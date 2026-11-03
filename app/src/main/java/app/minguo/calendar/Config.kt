package app.minguo.calendar

/** 发布相关配置：按需修改后重新编译 */
object AppConfig {
    const val AUTHOR = "StephenJose_Dai"
    /** 作者主页：留空就不显示 */
    const val BLOG = "https://www.gov.tw"

    /** “关于”页的 GitHub、Telegram 地址：填了才显示，留空就不显示 */
    const val GITHUB_URL = "https://github.com/StephenJose-Dai/minguoclock/"   // 例如 https://github.com/你的用户名/minguoclock
    const val TELEGRAM_URL = "https://t.me/StephenJose" // 例如 https://t.me/你的用户名或频道名

    /** 反馈：填了邮箱就优先发邮件，否则打开反馈网页 */
    const val FEEDBACK_EMAIL = "stephenjose_dai@zwyk.dpdns.org"
    const val FEEDBACK_URL = "https://github.com/StephenJose-Dai/minguoclock/issues" // 邮箱和网页都留空时，设置里不显示“反馈”

    /**
     * 检查更新：放一个 JSON 文件到你的服务器，格式：
     * {"versionCode":5,"versionName":"3.1.0","url":"https://…/MinguoClock.apk","notes":"更新说明"}
     */
    const val UPDATE_URL = "" // 留空时，设置里不显示“检查更新”

    /**
     * 高德地图 Web 服务：把定位坐标换成“省 市 区 街道”（逆地理编码）。可选，不填就用内置离线区县表。
     *
     * 这里填的就是“公共接口”：编译进所有安装包，所有用户共用这个 Key 的调用次数，所以有频率限制
     * （注意：仓库公开的话 Key 会被别人看到）。
     * 用户也可以在 App 里“设置 → 天气 → 天气接口”填自己的 Key，App 里填的优先，就不受公共接口限制。
     *
     * AMAP_KEY：高德开放平台 → 应用管理 → 添加 Key，服务平台选「Web服务」
     * AMAP_SECRET：如果给这个 Key 开了「数字签名」，填它的安全密钥（私钥）；没开就留空
     * AMAP_REGEO_URL：接口地址，一般不用改
     */
    const val AMAP_KEY = "15999063c76b38d2f3ec102f5b830648"
    const val AMAP_SECRET = ""
    const val AMAP_REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"


}
