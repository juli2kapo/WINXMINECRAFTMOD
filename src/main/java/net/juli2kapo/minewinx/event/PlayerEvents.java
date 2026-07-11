package net.juli2kapo.minewinx.event;

/**
 * Vacío a propósito: la lógica de vuelo que vivía acá se migró a
 * ServerEvents.tickPlayer porque PlayerTickEvent / PlayerLoggedInEvent no
 * llegan en este entorno (verificado con logs de diagnóstico). Las winx ya no
 * vuelan, y la limpieza de vuelo residual corre desde el server tick.
 */
public class PlayerEvents {
}
