class TooManyRequestException(message : String,val tryAfter : Long) : Exception(message) {
}