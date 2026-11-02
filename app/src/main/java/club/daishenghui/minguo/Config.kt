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


}
