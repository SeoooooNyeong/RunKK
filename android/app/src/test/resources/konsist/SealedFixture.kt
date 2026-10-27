package fixture.sealed

sealed interface Message

data class First(val id: Long) : Message

data object Second : Message
