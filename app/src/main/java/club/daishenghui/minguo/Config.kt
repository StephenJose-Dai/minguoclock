package club.daishenghui.minguo

/** 发布相关配置：按需修改后重新编译 */
object AppConfig {
    const val AUTHOR = "StephenJose_Dai"
    const val BLOG = "https://daishenghui.club"

    /** 反馈：填了邮箱就优先发邮件，否则打开反馈网页 */
    const val FEEDBACK_EMAIL = ""
    const val FEEDBACK_URL = "https://daishenghui.club"

    /**
     * 检查更新：放一个 JSON 文件到你的服务器，格式：
     * {"versionCode":5,"versionName":"3.1.0","url":"https://…/MinguoClock.apk","notes":"更新说明"}
     */
    const val UPDATE_URL = "https://daishenghui.club/minguo/version.json"

    /**
     * 高德地图 Web 服务：把定位坐标换成“省 市 区 街道”（逆地理编码）。可选，不填就用内置离线区县表。
     *
     * 两个地方都能填，App 里填的优先：
     * 1. App 里：设置 → 天气 → 高德地图（推荐：Key 只存在手机上，不会进代码仓库）；
     * 2. 这里：填好后重新编译，所有安装包都自带（注意：仓库公开的话 Key 会被别人看到）。
     *
     * AMAP_KEY：高德开放平台 → 应用管理 → 添加 Key，服务平台选「Web服务」
     * AMAP_SECRET：如果给这个 Key 开了「数字签名」，填它的安全密钥（私钥）；没开就留空
     * AMAP_REGEO_URL：接口地址，一般不用改
     */
    const val AMAP_KEY = "15999063c76b38d2f3ec102f5b830648"
    const val AMAP_SECRET = ""
    const val AMAP_REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"


}
