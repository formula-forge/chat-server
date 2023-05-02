package utilities

object CheckUtility {
    fun checkNotNull(vararg args:Any?):Boolean{
        for (arg in args){
            if (arg == null){
                return false
            }
        }
        return true
    }

    suspend fun checkVerifyCode(verifyCode: Int?, phone: String?) : Boolean {
        return true
    }

    fun checkSpecialChars(s:String):Boolean{
        val acceptable = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '_' + '-' + '.' + ' '
        for (c in s){
            if (!acceptable.contains(c))
                return false
        }
        return true
    }

    fun checkPassword(s: String):Boolean{
        if (s.length < 8)
            return false
        var num:Boolean = false
        var alpha:Boolean = false
        var Alpha:Boolean = false
        var sign:Boolean = false

        for (c in s){
            if (c in '0'..'9')
                num = true
            else if (c in 'a' .. 'z')
                alpha = true
            else if (c in 'A' .. 'Z')
                Alpha = true
            else if (c in "!@#\$%^&*()_+{}|:<>?[];',./`~")
                sign = true
        }

        return booleanArrayOf(num , alpha , Alpha , sign).count{ x -> return x} >= 3
    }

    fun checkPhone(s : String) : Boolean {
        return s.matches(Regex("^1[3-9]\\d{9}\$"))
    }
}