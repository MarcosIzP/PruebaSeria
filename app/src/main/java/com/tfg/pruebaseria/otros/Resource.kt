package com.tfg.pruebaseria.otros

//Estamos diciendo que el tipo de clase será de out of Type, que significa que no distinguirá entre variables
//Esto se hace porque como tratamos con duraciones de cacnciones por minutos,
// se puede dar el caso que una duracion sea un número entero, y otra sea decimal (double o float), de esta manera manejaremos ese psible error
//contendrá como parámetros una clase enum que será el estado, cosa que ya hemos declarado abajo
//el segundo parámetro será la propia data que el recurso contendrá, esta data sera de tipo T (lo que he explicado antes), y será nullable
//como tercer parámetro será un mensaje par informar en cualquier caso
data class Resource<out T>(val status: Status, val data: T?, val message : String?) {

    companion object{
        //en caso de que salga bien le pasamos los datos
        fun <T> success (data : T?)= Resource(Status.SUCCESS, data, null)

        //si ocurre un error pasaremos un mensaje y intentaremos obtener todos los datos posibles
        fun <T> error (message: String?, data : T?) = Resource(Status.ERROR, data, message)

        //Cuando esté cargando también le pasamos data, por esos posibles datos que estén guardados en caché mientras se cargan los nuevos datos
        fun <T> loading (data : T?) = Resource(Status.LOADING, data, null)
    }

}

enum class Status {
    SUCCESS,
    ERROR,
    LOADING
}