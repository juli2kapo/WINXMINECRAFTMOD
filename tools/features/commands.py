"""Text for the mod's general commands (/factoryascent charge)."""


def generate(ctx):
    m = "message.factoryascent.charge"
    ctx.lang(f"{m}.empty_hand", "Hold the item to charge in your main hand", "Sostén en la mano el objeto que quieras cargar")
    ctx.lang(f"{m}.no_energy", "%s doesn't store energy", "%s no almacena energía")
    ctx.lang(f"{m}.air", "Refilled the air tank of %s", "Se rellenó el tanque de aire de %s")
    ctx.lang(f"{m}.done", "Charged %s: +%s FE (%s / %s FE)", "Cargado %s: +%s FE (%s / %s FE)")
