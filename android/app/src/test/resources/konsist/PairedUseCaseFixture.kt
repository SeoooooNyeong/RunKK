package fixture.paired

class LoginUseCase {
    fun invoke(): Int = shared()
}

class SignUpUseCase {
    fun invoke(): Int = shared()
}

private fun shared(): Int = 1
