"""The Factory Ascent Manual: item model, recipe, advancement and every text of the book (EN + ES).

The book's table of contents lives in Java (guide/GuideEntries.java, guide/GuideMultiblocks.java);
this module writes the words. Markup inside entry texts: a newline starts a paragraph, "# " a
heading, "- " a bullet, [[item_id]] / [[item_id|label]] link to an item, [[@entry]] to an entry,
**bold**. generate() warns about entries or multiblock layouts that have no text.
"""
import re
from pathlib import Path

MOD = "factoryascent"
JAVA = Path(__file__).resolve().parents[2] / "src/main/java/net/juli2kapo/factoryascent/guide"

# ---------------------------------------------------------------- user interface

UI = {
    "title": ("Factory Ascent Manual", "Manual de Factory Ascent"),
    "search": ("Search entries and items…", "Buscar entradas y objetos…"),
    "back": ("Back", "Atrás"),
    "home": ("Home", "Inicio"),
    "chapters": ("Chapters", "Capítulos"),
    "results": ("Search results", "Resultados"),
    "entries": ("Entries", "Entradas"),
    "notes": ("Notes", "Notas"),
    "locked": ("Locked: unlocks with the advancement", "Bloqueada: se desbloquea con el logro"),
    "open_entry": ("Open this entry", "Abrir esta entrada"),
    "link_hint": ("Click: open in the Manual", "Clic: abrir en el Manual"),
    "link_hint_jei": ("Click: open in the Manual · Right-click: JEI recipes (shift: uses)",
                      "Clic: abrir en el Manual · Clic derecho: recetas en JEI (mayús: usos)"),
    "tab.structure": ("3D view", "Vista 3D"),
    "tab.materials": ("Materials", "Materiales"),
    "tab.recipes": ("Recipes", "Recetas"),
    "all_layers": ("All %s layers", "Las %s capas"),
    "layer": ("Layer %s / %s", "Capa %s / %s"),
    "size": ("%s × %s × %s", "%s × %s × %s"),
    "layer_down": ("Previous layer (from the top: all → last)", "Capa anterior"),
    "layer_up": ("Next layer (after the last: all)", "Capa siguiente (tras la última: todas)"),
    "all": ("All", "Todo"),
    "auto_rotate": ("Auto-rotate", "Girar solo"),
    "project": ("Project here", "Proyectar"),
    "project_tip": ("Projects a hologram of this structure where you are looking, its front facing you, "
                    "layer by layer. Sneak-use the Manual on a block to move it, in the air to turn it.",
                    "Proyecta un holograma de la estructura donde miras, con el frente hacia ti, capa por capa. "
                    "Agáchate y usa el Manual sobre un bloque para moverlo, o en el aire para girarlo."),
    "viewer_hint": ("Drag to rotate · scroll to zoom · hover a block for its name",
                    "Arrastra para girar · rueda para acercar · pasa el ratón por un bloque para ver su nombre"),
    "bom": ("Bill of materials: %s blocks", "Lista de materiales: %s bloques"),
    "have": ("have %s", "tienes %s"),
    "no_items": ("Nothing to craft here.", "Nada que fabricar aquí."),
    "used_in": ("%s is used in", "%s se usa en"),
    "made_by": ("Making %s", "Cómo hacer %s"),
    "show_recipes": ("Recipes", "Recetas"),
    "show_uses": ("Uses", "Usos"),
    "no_recipe": ("No recipe: found in the world, dropped by mobs or made by a multiblock.",
                  "Sin receta: se encuentra en el mundo, lo sueltan criaturas o lo hace un multibloque."),
    "no_recipes_synced": ("Recipes arrive when you join a world.", "Las recetas llegan al entrar en un mundo."),
    "item.nothing": ("A part of Factory Ascent.", "Una pieza de Factory Ascent."),
    "item.machines": ("Machines that use it", "Máquinas que lo usan"),
    "item.see": ("In the Manual", "En el Manual"),
    "open_manual": ("Open the Manual", "Abrir el Manual"),
    "open_manual_tip": ("Shows this machine's page: layout, recipes and tips",
                        "Muestra la página de esta máquina: estructura, recetas y consejos"),
    "holo.started": ("Projecting %s: build the glowing layer", "Proyectando %s: construye la capa que brilla"),
    "holo.done": ("%s complete!", "¡%s completo!"),
    "holo.hud": ("%s · layer %s/%s · %s blocks to go", "%s · capa %s/%s · faltan %s bloques"),
    "holo.keys": ("%s / %s: layer · %s: stop · sneak-use Manual: on a block move, in the air turn",
                  "%s / %s: capa · %s: quitar · Manual agachado: en un bloque mover, en el aire girar"),
    "holo.stop": ("Stop hologram", "Quitar holograma"),
    "holo.turn": ("Wrong way: turn the %s to face %s (place it looking the other way)",
                  "Mal orientado: gira el %s para que mire al %s (colócalo mirando hacia el otro lado)"),
    "holo.dir.north": ("north", "norte"),
    "holo.dir.south": ("south", "sur"),
    "holo.dir.east": ("east", "este"),
    "holo.dir.west": ("west", "oeste"),
    "holo.dir.up": ("up", "arriba"),
    "holo.dir.down": ("down", "abajo"),
    "home.text": (
        "Welcome, engineer. This book follows you from a hand-cranked mill to a swarm around the sun.\n"
        "# How to use it\n"
        "- Chapters on the right: one per age, then topics.\n"
        "- **Blue words** are links: click to open the item's page, right-click for JEI.\n"
        "- Entries marked **???** open when you earn the advancement shown in their tooltip.\n"
        "- Multiblock pages have a **3D view**: drag to turn, scroll to zoom, step through the layers, "
        "and **Project here** to build along a hologram.\n"
        "- Machine screens have a little book tab on top: it opens the machine's page.\n"
        "Lost? Read [[@welcome]] and [[@ages]].",
        "Bienvenido, ingeniero. Este libro te acompaña desde un molino de mano hasta un enjambre alrededor del sol.\n"
        "# Cómo usarlo\n"
        "- Capítulos a la derecha: uno por era y luego temas.\n"
        "- Las **palabras azules** son enlaces: clic para abrir la página del objeto, clic derecho para JEI.\n"
        "- Las entradas marcadas **???** se abren al conseguir el logro que indica su descripción.\n"
        "- Las páginas de multibloques tienen una **vista 3D**: arrastra para girar, rueda para acercar, recorre "
        "las capas y **Proyectar** para construir siguiendo un holograma.\n"
        "- Las pantallas de las máquinas tienen una pestaña con un libro arriba: abre la página de la máquina.\n"
        "¿Perdido? Lee [[@welcome]] y [[@ages]]."),
}

CHAPTERS = {
    "start": ("Getting Started", "Primeros pasos",
              "The basics of the mod and of this book.",
              "Lo básico del mod y de este libro."),
    "stone": ("Stone Age", "Edad de Piedra",
              "Hand tools and the first machines that need no fuel: hammer, quern, sieve, drying rack, "
              "water wheel and windmill. Goal: tin and the [[brick_kiln]] for bronze.",
              "Herramientas de mano y las primeras máquinas sin combustible: martillo, molino, criba, secadero, "
              "rueda hidráulica y molino de viento. Meta: estaño y el [[brick_kiln]] para el bronce."),
    "bronze": ("Bronze Age", "Edad del Bronce",
               "Burner machines, pipes and the first two multiblocks: the [[coke_oven]] and the [[blast_furnace]]. "
               "Steel ends this age.",
               "Máquinas a combustión, tuberías y los dos primeros multibloques: el [[coke_oven]] y el "
               "[[blast_furnace]]. El acero cierra esta era."),
    "electric": ("Electric Age", "Era Eléctrica",
                 "Machine frames, generators, cables and circuits. Every electric machine needs a [[basic_circuit]].",
                 "Chasis, generadores, cables y circuitos. Toda máquina eléctrica necesita un [[basic_circuit]]."),
    "automation": ("Automation Age", "Era de la Automatización",
                   "Advanced circuits, the Ore Miner, washing, oil and the machines that work while you are away.",
                   "Circuitos avanzados, el minero de menas, el lavado, el petróleo y las máquinas que trabajan "
                   "mientras no estás."),
    "industrial": ("Industrial Age", "Era Industrial",
                   "Titanium, heavy machines, uranium and the fission reactor.",
                   "Titanio, máquinas pesadas, uranio y el reactor de fisión."),
    "orbital": ("Orbital Age", "Era Orbital",
                "Rockets, satellites and the Plasma Forge.",
                "Cohetes, satélites y la forja de plasma."),
    "quantum": ("Quantum Age", "Era Cuántica",
                "Quantum alloy, fusion power and the Dyson project.",
                "Aleación cuántica, fusión y el proyecto Dyson."),
    "power": ("Power", "Energía",
              "Everything that makes, moves and stores FE, from a coal generator to a wind farm.",
              "Todo lo que produce, mueve y guarda FE, de un generador de carbón a un parque eólico."),
    "fluids": ("Fluids", "Fluidos",
               "Pipes, tanks, pumps and the oil industry.",
               "Tuberías, tanques, bombas y la industria del petróleo."),
    "logistics": ("Logistics", "Logística",
                  "Moving and storing items: pipes, crates and the storage network.",
                  "Mover y guardar objetos: tuberías, cajones y la red de almacenamiento."),
    "nuclear": ("Nuclear", "Nuclear",
                "Uranium, the fission reactor, radiation and waste. Read [[@reactor_heat]] before you load fuel.",
                "Uranio, el reactor de fisión, la radiación y los residuos. Lee [[@reactor_heat]] antes de cargar combustible."),
    "space": ("Space", "Espacio",
              "Getting to orbit and staying alive there: suits, air, stations, shuttles and planets.",
              "Llegar a la órbita y sobrevivir allí: trajes, aire, estaciones, lanzaderas y planetas."),
    "dyson": ("Dyson Project", "Proyecto Dyson",
              "Wrap the sun in solar collectors and beam its power home.",
              "Envuelve el sol en colectores solares y trae su energía a casa."),
    "ender": ("Ender & Cross-Dimension", "Ender e interdimensional",
              "Chunk loading, recall and links that move items, fluids and energy between worlds.",
              "Carga de chunks, regreso a casa y enlaces que mueven objetos, fluidos y energía entre mundos."),
    "ships": ("Ships", "Barcos",
              "Sailing ships, motor ships and the orbital shuttle.",
              "Veleros, barcos a motor y la lanzadera orbital."),
    "trains": ("Trains", "Trenes",
               "Locomotives and wagons on minecart rails: couple them into trains, stop them at stations and load them "
               "automatically.",
               "Locomotoras y vagones sobre rieles de vagoneta: engánchalos en trenes, detenlos en estaciones y cárgalos "
               "automáticamente."),
    "phone": ("Factory Phone", "Teléfono de fábrica",
              "Your factory in your pocket.",
              "Tu fábrica en el bolsillo."),
}

SHORT = {"start": ("Start", "Inicio"), "stone": ("Stone", "Piedra"), "bronze": ("Bronze", "Bronce"),
         "electric": ("Electric", "Eléctrica"), "automation": ("Automation", "Automatiz."),
         "industrial": ("Industrial", "Industrial"), "orbital": ("Orbital", "Orbital"), "quantum": ("Quantum", "Cuántica"),
         "ender": ("Ender", "Ender"), "dyson": ("Dyson", "Dyson"), "phone": ("Phone", "Teléfono")}

# multiblock layout names (+ short names for the variant buttons)
MB = {
    "coke_oven": ("Coke Oven", "3×3×3", "Horno de coque", "3×3×3"),
    "blast_furnace": ("Blast Furnace", "3×3×3", "Alto horno", "3×3×3"),
    "fission_reactor_5": ("Fission Reactor 5×5×5 (recommended)", "5³ ★", "Reactor de fisión 5×5×5 (recomendado)", "5³ ★"),
    "fission_reactor_3": ("Fission Reactor 3×3×3 (starter)", "3³", "Reactor de fisión 3×3×3 (inicial)", "3³"),
    "fission_reactor_7": ("Fission Reactor 7×7×7 (large)", "7³", "Reactor de fisión 7×7×7 (grande)", "7³"),
    "tokamak": ("Fusion Tokamak", "7×7×3", "Tokamak de fusión", "7×7×3"),
    "oil_derrick": ("Oil Derrick", "3×3", "Torre petrolera", "3×3"),
    "refinery": ("Refinery", "1×4×1", "Refinería", "1×4×1"),
    "launch_pad": ("Launch Pad", "3×3", "Plataforma de lanzamiento", "3×3"),
    "mass_driver": ("Mass Driver", "1×5×1", "Lanzador de masas", "1×5×1"),
    "dyson_receiver": ("Dyson Receiver", "3×3", "Receptor Dyson", "3×3"),
    "wind_turbine": ("Wind Turbine", "mast", "Aerogenerador", "mástil"),
    "ender_anchor": ("Ender Anchor", "1×2×1", "Ancla de Ender", "1×2×1"),
}

NOTES = {
    "hollow": ("The centre block must stay **air**: the fire burns there.",
               "El bloque central debe quedar **vacío**: ahí arde el fuego."),
    "reactor_3": ("Smallest reactor: one channel, no control rods, little cooling (volume 27). Good for learning, "
                  "not for power.",
                  "El reactor más pequeño: un canal, sin barras de control y poca refrigeración (volumen 27). "
                  "Sirve para aprender, no para producir."),
    "reactor_5": ("21 fuel channels in packed columns (they heat each other: more power, better burn-up) and 6 "
                  "control rods: one per four channels gives **full control**. Glass front and sides to watch the "
                  "core; Access Port left, Coolant Port right, Power Port at the back.",
                  "21 canales en columnas juntas (se calientan entre sí: más potencia y mejor quemado) y 6 barras de "
                  "control: una por cada cuatro canales da **control total**. Frente y laterales de vidrio para ver "
                  "el núcleo; puerto de acceso a la izquierda, de refrigerante a la derecha y de energía detrás."),
    "reactor_7": ("100 channels and 25 control rods (one per four), a Redstone Port on top for SCRAM and a "
                  "comparator. Needs a lot of coolant: pipe water into the Coolant Port or surround it with water "
                  "sources.",
                  "100 canales y 25 barras de control (una por cada cuatro), un puerto de redstone arriba para el SCRAM "
                  "y un comparador. Necesita mucho refrigerante: entuba agua al puerto de refrigerante o rodéalo de "
                  "fuentes de agua."),
    "tokamak": ("Middle layer: core, a ring of magnets, a ring of **air** (the plasma channel), the outer magnet "
                "ring. Top and bottom: a magnet over and under the core, casing (or Reactor Glass, or Fusion Ports) "
                "around it.",
                "Capa central: núcleo, un anillo de imanes, un anillo de **aire** (el canal del plasma) y el anillo "
                "exterior de imanes. Arriba y abajo: un imán sobre y bajo el núcleo, y carcasa (o vidrio de reactor, "
                "o puertos de fusión) alrededor."),
    "oil": ("Build it over an oil pocket: it drills straight down. Pipes on the bases take the crude.",
            "Constrúyela sobre una bolsa de petróleo: perfora en vertical. Las tuberías en las bases sacan el crudo."),
    "clearance": ("Keep 9 blocks of open air above the controller: the rocket stands there.",
                  "Deja 9 bloques de aire libre sobre el controlador: ahí se alza el cohete."),
    "sky": ("Needs open sky above the top rail (always open in space).",
            "Necesita cielo abierto sobre el último riel (en el espacio siempre lo hay)."),
    "daylight": ("Needs open sky and daylight; in space it never sets. Energy comes out of the centre block's bottom.",
                 "Necesita cielo abierto y luz de día; en el espacio nunca se pone el sol. La energía sale por abajo "
                 "del bloque central."),
    "rotor": ("The 5×5 disc in front of the nacelle (corners free) must be clear for the blades. Higher is windier.",
              "El disco de 5×5 delante de la góndola (sin las esquinas) debe estar libre para las aspas. Más alto, más viento."),
    "mast_power": ("At least 4 Turbine Masts. The power comes out under the foot of the mast: put a cable or a cell there.",
                   "Al menos 4 mástiles. La energía sale bajo el pie del mástil: pon ahí un cable o una celda."),
    "pearl": ("Place it as one block (it is two tall). Right-click with an ender pearl to start it.",
              "Se coloca como un bloque (mide dos). Clic derecho con una perla de ender para encenderla."),
}

# ---------------------------------------------------------------- entries: id -> (title en, text en, title es, text es)

E = {}


def entry(id, title_en, text_en, title_es, text_es):
    E[id] = (title_en, text_en, title_es, text_es)


# ---- Getting started
entry("welcome", "Welcome", (
    "Factory Ascent is a climb through seven ages: **Stone, Bronze, Electric, Automation, Industrial, Orbital, "
    "Quantum**. Each age ends with a breakthrough advancement that opens the next one.\n"
    "Start by crafting a [[forge_hammer]] and a [[quern]]: grind ore into dust for extra ingots.\n"
    "This book grows with you: entries open as you earn advancements."),
    "Bienvenida", (
    "Factory Ascent es un ascenso por siete eras: **Piedra, Bronce, Eléctrica, Automatización, Industrial, Orbital y "
    "Cuántica**. Cada era termina con un logro de avance que abre la siguiente.\n"
    "Empieza fabricando un [[forge_hammer]] y un [[quern]]: muele mineral en polvo para conseguir lingotes de más.\n"
    "Este libro crece contigo: las entradas se abren a medida que consigues logros."))
entry("ages", "The Seven Ages", (
    "# Stone\nHammer plates by hand, grind ore in the [[quern]], find tin and fire the [[brick_kiln]].\n"
    "# Bronze\nBronze tools, burner machines, [[@coke_oven]] and [[@blast_furnace]] for steel.\n"
    "# Electric\n[[machine_frame|Machine frames]], generators and cables, the [[assembler]] and [[basic_circuit|circuits]].\n"
    "# Automation\n[[advanced_circuit|Advanced circuits]], the [[miner]], oil and automatic farms.\n"
    "# Industrial\nTitanium from the [[induction_smelter]], uranium and the [[@fission_reactor]].\n"
    "# Orbital\nThe [[@launch_pad]], satellites, crewed flight and the [[plasma_forge]].\n"
    "# Quantum\n[[quantum_alloy_ingot|Quantum alloy]], the [[@tokamak]] and the [[@dyson_overview|Dyson project]]."),
    "Las siete eras", (
    "# Piedra\nPlacas a martillo, mineral al [[quern]], estaño y el [[brick_kiln]].\n"
    "# Bronce\nHerramientas de bronce, máquinas a combustión, [[@coke_oven]] y [[@blast_furnace]] para el acero.\n"
    "# Eléctrica\n[[machine_frame|Chasis]], generadores y cables, la [[assembler]] y los [[basic_circuit|circuitos]].\n"
    "# Automatización\n[[advanced_circuit|Circuitos avanzados]], el [[miner]], petróleo y granjas automáticas.\n"
    "# Industrial\nTitanio en la [[induction_smelter]], uranio y el [[@fission_reactor]].\n"
    "# Orbital\nLa [[@launch_pad]], satélites, vuelos tripulados y la [[plasma_forge]].\n"
    "# Cuántica\n[[quantum_alloy_ingot|Aleación cuántica]], el [[@tokamak]] y el [[@dyson_overview|proyecto Dyson]]."))
entry("multiblocks", "Multiblocks", (
    "Some machines are made of many blocks around a **controller**. Until the shape is right the controller "
    "says **Structure incomplete** and does nothing.\n"
    "Every multiblock page has a **3D view**: drag to rotate, scroll to zoom, use < and > to see one layer at a "
    "time (the layers below are ghosted), hover a block to see what it is. **Materials** counts every block and "
    "how many you carry.\n"
    "The multiblocks: [[@coke_oven]], [[@blast_furnace]], [[@oil_derrick]], [[@refinery]], [[@wind_turbine]], "
    "[[@fission_reactor]], [[@launch_pad]], [[@tokamak]], [[@mass_driver]], [[@dyson_receiver]], [[@ender_anchor]]."),
    "Multibloques", (
    "Algunas máquinas se forman con muchos bloques alrededor de un **controlador**. Mientras la forma no sea la "
    "correcta, el controlador dice **Estructura incompleta** y no hace nada.\n"
    "Cada página de multibloque tiene una **vista 3D**: arrastra para girar, rueda para acercar, usa < y > para ver "
    "una capa cada vez (las de abajo se ven translúcidas) y pasa el ratón por un bloque para saber cuál es. "
    "**Materiales** cuenta cada bloque y cuántos llevas.\n"
    "Los multibloques: [[@coke_oven]], [[@blast_furnace]], [[@oil_derrick]], [[@refinery]], [[@wind_turbine]], "
    "[[@fission_reactor]], [[@launch_pad]], [[@tokamak]], [[@mass_driver]], [[@dyson_receiver]], [[@ender_anchor]]."))
entry("hologram", "Building with Holograms", (
    "On any multiblock page press **Project here**: the book closes and a hologram of the structure appears where "
    "you were looking, its front facing you.\n"
    "- The layer to build glows and pulses; the next one is a faint preview.\n"
    "- Blocks in the way are tinted **red**.\n"
    "- Controllers must **face out** of the structure: one placed the wrong way turns red, shows the right facing "
    "inside it and the HUD says which way to turn it; the layer only counts once it faces right. Turning the "
    "hologram turns the facings too.\n"
    "- When a layer is complete the hologram moves up by itself; when the structure stands it disappears with a fanfare.\n"
    "- Sneak-use the Manual on a block to move the hologram there, or in the air to turn it 90°.\n"
    "- **]** and **[** change the layer, **\\** removes the hologram (Controls → Factory Ascent: Manual).\n"
    "Nothing is placed for you: it is only light."),
    "Construir con hologramas", (
    "En cualquier página de multibloque pulsa **Proyectar**: el libro se cierra y aparece un holograma de la "
    "estructura donde mirabas, con el frente hacia ti.\n"
    "- La capa a construir brilla y late; la siguiente se ve tenue.\n"
    "- Los bloques que estorban se tiñen de **rojo**.\n"
    "- Los controladores deben **mirar hacia fuera** de la estructura: uno mal orientado se ve rojo, con la "
    "orientación correcta dentro, y el HUD dice hacia dónde girarlo; la capa solo cuenta cuando mira bien. Al girar "
    "el holograma también giran las orientaciones.\n"
    "- Al completar una capa el holograma sube solo; cuando la estructura está en pie desaparece con una fanfarria.\n"
    "- Agáchate y usa el Manual sobre un bloque para mover el holograma allí, o en el aire para girarlo 90°.\n"
    "- **]** y **[** cambian de capa, **\\** quita el holograma (Controles → Factory Ascent: Manual).\n"
    "No coloca nada por ti: es solo luz."))

# ---- Stone
entry("hammer", "Forge Hammer", (
    "The first tool: craft the [[forge_hammer]] with 2 ingots of the same metal to get a **plate** "
    "(for example a [[copper_plate]]). Plates are in almost every early recipe. The hammer takes damage with each use."),
    "Martillo de forja", (
    "La primera herramienta: fabrica el [[forge_hammer]] con 2 lingotes del mismo metal para obtener una **placa** "
    "(por ejemplo una [[copper_plate]]). Las placas están en casi todas las primeras recetas. El martillo se desgasta con cada uso."))
entry("quern", "Quern", (
    "The [[quern]] grinds raw ore into dust: 1 dust plus a 50% chance of a second. Smelt the dust for ingots.\n"
    "Sneak + right-click it, or press the Crank button on its screen. A [[water_wheel]] or [[windmill]] next to it "
    "turns it for you (see [[@kinetic]])."),
    "Molino de mano", (
    "El [[quern]] muele mineral en bruto en polvo: 1 polvo más un 50% de otro. Funde el polvo para obtener lingotes.\n"
    "Agáchate + clic derecho, o pulsa Girar en su pantalla. Una [[water_wheel]] o un [[windmill]] al lado lo giran por ti "
    "(mira [[@kinetic]])."))
entry("sieve", "Sieve", (
    "The [[sieve]] sifts gravel, sand, dirt and soul sand for flint, clay, seeds and metal nuggets. Crank it like the "
    "quern, or let a water wheel do it. A good way to find tin early."),
    "Criba", (
    "La [[sieve]] criba grava, arena, tierra y arena de almas en busca de pedernal, arcilla, semillas y pepitas. Gírala "
    "como el molino o deja que lo haga una rueda hidráulica. Buena forma de conseguir estaño al principio."))
entry("drying_rack", "Drying Rack", (
    "The [[drying_rack]] air-dries what hangs on it: rotten flesh into leather, kelp, wet sponges. No fuel, only time."),
    "Secadero", (
    "El [[drying_rack]] seca al aire lo que cuelga: carne podrida en cuero, algas, esponjas mojadas. Sin combustible, solo tiempo."))
entry("kinetic", "Water Wheels and Windmills", (
    "A [[water_wheel]] turns in water (flowing water is fastest); a [[windmill]] turns in the wind, faster up high "
    "and in storms, and needs a 3×3 of air in front of its sails. Either one turns the handle of a [[quern]] or "
    "[[sieve]] touching it.\nLater a [[kinetic_dynamo]] turns that rotation into FE."),
    "Ruedas hidráulicas y molinos", (
    "Una [[water_wheel]] gira con el agua (la corriente es más rápida); un [[windmill]] gira con el viento, más rápido "
    "en altura y con tormenta, y necesita 3×3 de aire delante de sus aspas. Ambos giran la manivela de un [[quern]] o "
    "una [[sieve]] que toquen.\nMás adelante una [[kinetic_dynamo]] convierte ese giro en FE."))
entry("tin_kiln", "Tin and the Brick Kiln", (
    "Tin ore shows pale specks and is common underground. Fire clay bricks into the [[brick_kiln]]: it burns "
    "furnace fuel and alloys **3 copper + 1 tin** into bronze. Your first bronze ingot opens the Bronze Age."),
    "El estaño y el horno de ladrillo", (
    "La mena de estaño tiene manchas claras y abunda bajo tierra. Con ladrillos de arcilla haz el [[brick_kiln]]: quema "
    "combustible y alea **3 cobre + 1 estaño** en bronce. Tu primer lingote de bronce abre la Edad del Bronce."))

# ---- Bronze
entry("bronze", "Bronze", (
    "Bronze tools and armour are tougher than stone and nearly as good as iron. Bronze plates and gears build "
    "the burner machines, pipes, crates and the [[bronze_cog]] sailing ship."),
    "Bronce", (
    "Las herramientas y armaduras de bronce superan a las de piedra y casi igualan al hierro. Las placas y engranajes "
    "de bronce construyen las máquinas a combustión, tuberías, cajones y el velero [[bronze_cog]]."))
entry("burner_machines", "Burner Machines", (
    "The [[burner_crusher]] gives **2 dust per raw ore**. The [[burner_press]] makes plates, gears, rods and wires; "
    "the **mould** in its mould slot decides which ([[plate_mold]], [[gear_mold]], [[rod_mold]], [[wire_mold]]). "
    "Moulds are never used up. Both burn furnace fuel."),
    "Máquinas a combustión", (
    "La [[burner_crusher]] da **2 polvos por mineral**. La [[burner_press]] hace placas, engranajes, varillas y cables; "
    "el **molde** en su ranura decide cuál ([[plate_mold]], [[gear_mold]], [[rod_mold]], [[wire_mold]]). Los moldes no "
    "se gastan. Ambas queman combustible."))
entry("coke_oven", "Coke Oven", (
    "Your first multiblock: a 3×3×3 cube of [[coke_oven_bricks]] with the [[coke_oven]] controller at the **bottom "
    "centre of the front face**, facing out. It bakes coal into [[coke]] (and logs into charcoal) and needs no fuel.\n"
    "Open the 3D view on the right, or press **Project here** and build along the hologram. Coke is the fuel of the "
    "[[@blast_furnace]]."),
    "Horno de coque", (
    "Tu primer multibloque: un cubo de 3×3×3 de [[coke_oven_bricks]] con el controlador [[coke_oven]] en el **centro "
    "de abajo de la cara frontal**, mirando hacia fuera. Convierte carbón en [[coke]] (y troncos en carbón vegetal) sin "
    "combustible.\nAbre la vista 3D a la derecha o pulsa **Proyectar** y construye siguiendo el holograma. El coque "
    "alimenta el [[@blast_furnace]]."))
entry("blast_furnace", "Blast Furnace", (
    "A 3×3×3 cube of [[fire_bricks]] with the [[blast_furnace]] controller at the bottom centre of the front face, "
    "and the **middle block left empty**. It turns iron into [[steel_ingot|steel]], burning [[coke]].\n"
    "Steel opens the Electric Age: machine frames are made of steel plates."),
    "Alto horno", (
    "Un cubo de 3×3×3 de [[fire_bricks]] con el controlador [[blast_furnace]] en el centro de abajo de la cara frontal "
    "y el **bloque central vacío**. Convierte hierro en [[steel_ingot|acero]] quemando [[coke]].\n"
    "El acero abre la Era Eléctrica: los chasis se hacen con placas de acero."))
entry("bronze_gear", "Bronze Gear", (
    "- [[bronze_backpack]]: extra slots on your back. Use it to open.\n"
    "- [[grappling_hook]]: aim at a block and use it to be pulled there.\n"
    "- Bronze armour: a full set beats leather and chain."),
    "Equipo de bronce", (
    "- [[bronze_backpack]]: ranuras extra a la espalda. Úsala para abrirla.\n"
    "- [[grappling_hook]]: apunta a un bloque y úsalo para que te arrastre hasta él.\n"
    "- Armadura de bronce: el conjunto completo supera al cuero y la malla."))

# ---- Electric
entry("machine_frame", "Machine Frame", (
    "Every electric machine starts as a [[machine_frame]] made from [[steel_plate|steel plates]]. Press steel in the "
    "[[burner_press]] with a plate mould."),
    "Chasis de máquina", (
    "Toda máquina eléctrica empieza como un [[machine_frame]] hecho de [[steel_plate|placas de acero]]. Prensa acero en la "
    "[[burner_press]] con un molde de placa."))
entry("first_power", "First Power", (
    "The [[combustion_generator]] burns furnace fuel for 40 FE/t. Connect machines with [[copper_cable]]: a network "
    "runs at the rate of its slowest cable. An [[energy_cell]] stores the surplus: power comes **out** of its "
    "lightning-bolt face and goes **in** through all the others (wrench a side to make it the output).\n"
    "More generators: [[@generators]]."),
    "Primera energía", (
    "El [[combustion_generator]] quema combustible por 40 FE/t. Conecta las máquinas con [[copper_cable]]: una red va al "
    "ritmo de su cable más lento. Una [[energy_cell]] guarda lo que sobra: la energía **sale** por la cara del rayo y "
    "**entra** por las demás (usa la llave en un lado para que sea la salida).\nMás generadores: [[@generators]]."))
entry("circuits", "Assembler and Circuits", (
    "The [[assembler]] builds circuits, motors and frames from parts. Your first [[basic_circuit]] is a breakthrough: "
    "every electric machine needs one. [[motor|Motors]] go into moving machines and ships."),
    "Ensambladora y circuitos", (
    "La [[assembler]] fabrica circuitos, motores y chasis a partir de piezas. Tu primer [[basic_circuit]] es un avance: "
    "toda máquina eléctrica necesita uno. Los [[motor|motores]] van en máquinas con movimiento y barcos."))
entry("electric_machines", "Electric Machines", (
    "- [[electric_furnace]]: smelts five times faster, and aluminium.\n"
    "- [[crusher]]: 2 dust per raw ore (3 per ore block) plus byproducts.\n"
    "- [[metal_press]]: a fast press; the mould decides.\n"
    "- [[alloy_smelter]]: bronze, steel and silicon, quickly.\n"
    "Every machine has upgrade slots: see [[@upgrades]]. The ? button on a machine screen lists what it accepts."),
    "Máquinas eléctricas", (
    "- [[electric_furnace]]: funde cinco veces más rápido, y aluminio.\n"
    "- [[crusher]]: 2 polvos por mineral (3 por bloque de mena) más subproductos.\n"
    "- [[metal_press]]: prensa rápida; el molde decide.\n"
    "- [[alloy_smelter]]: bronce, acero y silicio, rápido.\n"
    "Todas tienen ranuras de mejora: mira [[@upgrades]]. El botón ? de la pantalla muestra lo que aceptan."))
entry("electric_tools", "Electric Tools", (
    "- [[electric_drill]]: mines faster than netherite while charged (100 FE per block). Sneak-use in the air to pick "
    "single, 3×3 or vein mode.\n- [[charger]]: charges any FE item and hands it out when full.\n"
    "- [[item_magnet]] and [[night_vision_goggles]] run on FE too."),
    "Herramientas eléctricas", (
    "- [[electric_drill]]: pica más rápido que la netherita si está cargado (100 FE por bloque). Agáchate y úsalo en el "
    "aire para elegir modo simple, 3×3 o veta.\n- [[charger]]: carga cualquier objeto de FE y lo entrega lleno.\n"
    "- El [[item_magnet]] y las [[night_vision_goggles]] también usan FE."))
entry("auto_farmer", "Auto-Farmer", (
    "The [[auto_farmer]] harvests ripe crops in a 9×9 field in front of it and replants them. Pipe the harvest away."),
    "Granjero automático", (
    "El [[auto_farmer]] cosecha los cultivos maduros de un campo de 9×9 delante y los replanta. Saca la cosecha con tuberías."))
entry("aluminium_silicon", "Aluminium and Silicon", (
    "Bauxite smelts into [[aluminum_ingot|aluminium]] only in the [[electric_furnace]]. Alloy quartz dust and coal "
    "dust into [[silicon]] and press it into [[silicon_wafer|wafers]]: the way to advanced circuits."),
    "Aluminio y silicio", (
    "La bauxita solo se funde en [[aluminum_ingot|aluminio]] en el [[electric_furnace]]. Alea polvo de cuarzo y de "
    "carbón en [[silicon]] y prénsalo en [[silicon_wafer|obleas]]: el camino a los circuitos avanzados."))
entry("upgrades", "Upgrades", (
    "Electric machines have upgrade slots. [[speed_upgrade]]: +50% speed (uses more energy). "
    "[[energy_upgrade]]: -20% energy per operation. They stack."),
    "Mejoras", (
    "Las máquinas eléctricas tienen ranuras de mejora. [[speed_upgrade]]: +50% de velocidad (gasta más). "
    "[[energy_upgrade]]: -20% de energía por operación. Se acumulan."))

# ---- Automation
entry("advanced_circuit", "Advanced Circuit", (
    "Assemble an [[advanced_circuit]] from silicon wafers: the breakthrough into the Automation Age."),
    "Circuito avanzado", (
    "Ensambla un [[advanced_circuit]] con obleas de silicio: el avance a la Era de la Automatización."))
entry("miner", "Ore Miner", (
    "The [[miner]] claims its own chunk column in **The Deep**, a sealed mining dimension, and digs out its ore "
    "without touching your world. Give it power and a place to put the ore."),
    "Minero de menas", (
    "El [[miner]] reclama su propia columna de chunks en **Las Profundidades**, una dimensión minera sellada, y extrae "
    "sus menas sin tocar tu mundo. Dale energía y un sitio donde dejar el mineral."))
entry("ore_washer", "Ore Washer and Induction Smelter", (
    "The [[ore_washer]] washes raw ore into **3 dust** (4 from ore blocks) with better byproducts. The "
    "[[induction_smelter]] is the only smelter hot enough for titanium; it also smelts anything, very fast."),
    "Lavadora y fundidora de inducción", (
    "La [[ore_washer]] lava el mineral en **3 polvos** (4 de bloques de mena) con mejores subproductos. La "
    "[[induction_smelter]] es la única capaz de fundir titanio; además funde de todo, muy rápido."))
entry("automation_blocks", "Automation Blocks", (
    "- [[block_breaker]]: breaks the block in front and keeps the drops (a tool lends its enchantments).\n"
    "- [[block_placer]]: places blocks from its inventory.\n- [[vacuum_hopper]]: pulls dropped items within 6 blocks.\n"
    "- [[tree_farm]]: plants saplings in a 7×7 field and fells the trees. It replants with the saplings it harvested "
    "(from its outputs) before using the ones you put in; spare saplings stay in the outputs."),
    "Bloques de automatización", (
    "- [[block_breaker]]: rompe el bloque de delante y guarda lo que suelta (una herramienta le presta sus encantamientos).\n"
    "- [[block_placer]]: coloca bloques de su inventario.\n- [[vacuum_hopper]]: atrae objetos sueltos a 6 bloques.\n"
    "- [[tree_farm]]: planta brotes en un campo de 7×7 y tala los árboles. Replanta con los brotes que cosechó "
    "(de sus salidas) antes de usar los que pongas tú; los que sobran se quedan en las salidas."))
entry("oil_derrick", "Oil Derrick", (
    "A pumpjack: the [[oil_derrick]] in the middle of a 3×3 platform of [[derrick_base|Derrick Bases]]. Once formed "
    "it drills straight down to the oil pocket under it and pumps 1000 mB of crude every 2 s for 60 FE/t. Pipes on "
    "the bases take the oil.\nIt only pumps where there is a crude oil pocket below. Refine the crude in the [[@refinery]]."),
    "Torre petrolera", (
    "Un balancín: la [[oil_derrick]] en medio de una plataforma de 3×3 de [[derrick_base|bases]]. Una vez formada perfora "
    "en vertical hasta la bolsa de petróleo y bombea 1000 mB de crudo cada 2 s por 60 FE/t. Las tuberías en las bases "
    "sacan el petróleo.\nSolo bombea si hay una bolsa de crudo debajo. Refina el crudo en la [[@refinery]]."))
entry("refinery", "Refinery", (
    "Stack three [[refinery_tower|Refinery Towers]] on the [[refinery]]. It splits each 1000 mB of crude into 500 mB of "
    "diesel, 250 mB of rocket fuel, one [[plastic]] and two [[tar]]. Pipe crude into any section and take the products "
    "from any section."),
    "Refinería", (
    "Apila tres [[refinery_tower|torres]] sobre la [[refinery]]. Separa cada 1000 mB de crudo en 500 mB de diésel, 250 mB "
    "de combustible de cohete, un [[plastic]] y dos [[tar]]. Mete el crudo por cualquier sección y saca los productos por "
    "cualquiera."))
entry("mobs", "Mob Tools", (
    "- [[mob_capsule]]: hold right-click on a mob for 3 s to trap it; right-click a block to release. It runs on FE "
    "(charge it in a [[charger]]): a capture takes half a full charge and a release the other half, so a full capsule "
    "does one of each. Without enough charge it refuses and says how much it needs.\n"
    "- [[mob_farm]]: a filled capsule inside produces that mob's drops with power, no mob needed.\n"
    "- [[minimizer_ray]] and [[maximizer_ray]]: shrink or grow creatures.\n"
    "- [[size_chamber]]: put a filled capsule in, pick a target size (within the rays' limits): it resizes the mob "
    "inside, then heals it to full, with FE. The mob keeps both when released.\n"
    "- [[mob_releaser]]: nine capsule slots; a redstone pulse or **Release All** lets every mob out at once, at a point "
    "you set in front of it. It pays with its own FE (10 kFE per mob by default), so the capsules in it need no charge.\n"
    "# Bosses\nA Wither beaten below 10% health can be captured (the Ender Dragon and other bosses can't). Heal it in a "
    "Size Chamber, shrink it, and release ten of them at once if you dare."),
    "Herramientas de criaturas", (
    "- [[mob_capsule]]: mantén clic derecho sobre una criatura 3 s para atraparla; clic derecho en un bloque para soltarla. "
    "Funciona con FE (cárgala en un [[charger]]): capturar gasta media carga y liberar la otra mitad, así que una cápsula "
    "llena hace una de cada. Sin carga suficiente se niega y dice cuánta necesita.\n"
    "- [[mob_farm]]: con una cápsula llena dentro produce lo que suelta esa criatura, con energía y sin la criatura.\n"
    "- [[minimizer_ray]] y [[maximizer_ray]]: encogen o agrandan criaturas.\n"
    "- [[size_chamber]]: pon una cápsula llena y elige un tamaño (dentro de los límites de los rayos): cambia el tamaño "
    "de la criatura y luego la cura por completo, con FE. Al soltarla conserva ambas cosas.\n"
    "- [[mob_releaser]]: nueve ranuras de cápsula; un pulso de redstone o **Liberar todo** suelta a todas a la vez, en "
    "un punto que eliges delante. Paga con su propia FE (10 kFE por criatura por defecto), así que las cápsulas que "
    "tiene dentro no necesitan carga.\n"
    "# Jefes\nUn Wither con menos del 10% de vida se puede capturar (el dragón del End y otros jefes no). Cúralo en una "
    "cámara de tamaño, encógelo y suelta diez a la vez si te atreves."))

# ---- Industrial
entry("titanium", "Titanium", (
    "Smelt titanium in the [[induction_smelter]] (the breakthrough into the Industrial Age) and press it in the "
    "[[hydraulic_press]] into [[titanium_plate|plates]] and gears."),
    "Titanio", (
    "Funde titanio en la [[induction_smelter]] (el avance a la Era Industrial) y prénsalo en la [[hydraulic_press]] en "
    "[[titanium_plate|placas]] y engranajes."))
entry("industrial_machines", "Industrial Machines", (
    "- [[industrial_grinder]]: 4 dust per raw ore, two byproduct chances.\n- [[recycler]]: junk into scrap, worn gear "
    "back into materials.\n- [[centrifuge]]: enriches uranium and reprocesses spent fuel.\n"
    "- [[industrial_energy_cell]]: 16× an energy cell."),
    "Máquinas industriales", (
    "- [[industrial_grinder]]: 4 polvos por mineral y dos probabilidades de subproducto.\n- [[recycler]]: basura en "
    "chatarra y equipo gastado en materiales.\n- [[centrifuge]]: enriquece uranio y reprocesa combustible gastado.\n"
    "- [[industrial_energy_cell]]: 16 veces una celda."))
entry("precision", "Precision Assembler", (
    "The [[precision_assembler]] is a clean-room assembler for spacecraft parts, such as the Orbital Targeting Core "
    "that opens the Orbital Age."),
    "Ensambladora de precisión", (
    "La [[precision_assembler]] es una ensambladora de sala limpia para piezas espaciales, como el núcleo de puntería "
    "orbital que abre la Era Orbital."))

# ---- Orbital
entry("launch_pad", "Launch Pad", (
    "Eight [[launch_pad|Launch Pad]] plates around a [[launch_controller]] make the 3×3 pad. Keep the air above it "
    "clear. Mount a satellite (or crew capsule), fuel it (Blaze Powder 1, Rocket Fuel 4 units) and launch with "
    "redstone or the screen's Launch button.\nHoppers and pipes can feed payloads and fuel; they launch for whoever "
    "placed the controller."),
    "Plataforma de lanzamiento", (
    "Ocho placas de [[launch_pad|plataforma]] alrededor de un [[launch_controller]] forman la plataforma de 3×3. Deja libre "
    "el aire de encima. Monta un satélite (o una cápsula), cárgale combustible (polvo de blaze 1, combustible de cohete 4) "
    "y lanza con redstone o el botón Lanzar.\nLas tolvas y tuberías pueden meter cargas y combustible; lanzan en nombre "
    "de quien colocó el controlador."))
entry("satellites", "Satellites", (
    "- [[survey_satellite]]: your [[ground_station|Ground Stations]] map the land around them, even land nobody has "
    "explored yet (guessed from the terrain generator, then imaged for real once the chunks are generated).\n"
    "- [[uplink_satellite]]: signal everywhere in the dimension (phone, wireless terminal).\n"
    "- [[guardian_satellite]]: stops the next anti-satellite missile.\n- [[orbital_radar]]: lists every satellite overhead.\n"
    "A satellite covers the dimension it is launched from."),
    "Satélites", (
    "- [[survey_satellite]]: tus [[ground_station|estaciones terrenas]] cartografían los alrededores, incluso tierras "
    "sin explorar (estimadas con el generador de terreno y fotografiadas de verdad cuando se generan).\n"
    "- [[uplink_satellite]]: señal en toda la dimensión (teléfono, terminal inalámbrica).\n"
    "- [[guardian_satellite]]: detiene el próximo misil antisatélite.\n- [[orbital_radar]]: lista todos los satélites.\n"
    "Un satélite cubre la dimensión desde la que se lanza."))
entry("plasma_forge", "Plasma Forge", (
    "The [[plasma_forge]] forges quantum alloy in a plasma arc. Your first [[orbital_targeting_core]] opens the "
    "Orbital Age; the forge opens the Quantum Age."),
    "Forja de plasma", (
    "La [[plasma_forge]] forja aleación cuántica en un arco de plasma. Tu primer [[orbital_targeting_core]] abre la Era "
    "Orbital; la forja abre la Cuántica."))

# ---- Quantum
entry("quantum_alloy", "Quantum Alloy", (
    "[[quantum_alloy_ingot|Quantum alloy]] builds the last machines: the [[quantum_energy_cell]] (64× a cell), the "
    "[[@tokamak]], the [[@mass_driver]] and the [[quantum_entangler]]."),
    "Aleación cuántica", (
    "La [[quantum_alloy_ingot|aleación cuántica]] construye las últimas máquinas: la [[quantum_energy_cell]] (64 veces una "
    "celda), el [[@tokamak]], el [[@mass_driver]] y el [[quantum_entangler]]."))
entry("tokamak", "Fusion Tokamak", (
    "A 7×7×3 ring around the [[tokamak_core]] in the middle of the middle layer. 34 [[fusion_magnet|magnets]], a ring "
    "of air for the plasma and a shell of [[fusion_casing]] (Reactor Glass works too). Put [[fusion_port|Fusion Ports]] "
    "in the shell.\n# Starting up\n- Switch it on in the screen (right-click a port).\n"
    "- Charge the magnets: feed the start-up energy through the Fusion Ports.\n"
    "- Load fuel: [[deuterium_cell|Deuterium]] and [[helium_3_fuel_cell|Helium-3]] cells.\n"
    "- One [[tritium_cell]] ignites the plasma.\n"
    "While burning it makes a huge flow of FE out of the ports. When fuel runs out it cools down safely and the "
    "magnets need a new charge.\n# Never break the ring while it burns\nA disruption blasts the wall and arcs lightning."),
    "Tokamak de fusión", (
    "Un anillo de 7×7×3 alrededor del [[tokamak_core]], en medio de la capa central. 34 [[fusion_magnet|imanes]], un anillo "
    "de aire para el plasma y una carcasa de [[fusion_casing]] (el vidrio de reactor también vale). Pon "
    "[[fusion_port|puertos de fusión]] en la carcasa.\n# Arranque\n- Enciéndelo en la pantalla (clic derecho en un puerto).\n"
    "- Carga los imanes: mete la energía de arranque por los puertos.\n"
    "- Carga combustible: celdas de [[deuterium_cell|deuterio]] y de [[helium_3_fuel_cell|helio-3]].\n"
    "- Una [[tritium_cell]] enciende el plasma.\n"
    "Encendido, saca un enorme flujo de FE por los puertos. Si se acaba el combustible se enfría sin peligro y hay que "
    "volver a cargar los imanes.\n# Nunca rompas el anillo encendido\nUna disrupción revienta la pared y lanza rayos."))
entry("fusion_fuel", "Fusion Fuel", (
    "The [[electrolyzer]] splits water: a water bucket and an Empty Cell make a [[deuterium_cell]]; four Deuterium "
    "Cells give a [[tritium_cell]]. [[helium_3_fuel_cell|Helium-3]] comes from Moon regolith (see [[@planets]])."),
    "Combustible de fusión", (
    "El [[electrolyzer]] separa el agua: un cubo de agua y una celda vacía dan una [[deuterium_cell]]; cuatro de deuterio "
    "dan una [[tritium_cell]]. El [[helium_3_fuel_cell|helio-3]] sale del regolito lunar (mira [[@planets]])."))

# ---- Power
entry("energy_basics", "Energy and Cables", (
    "Machines run on **FE**. Cables join everything they touch into one network, which runs at the rate of its "
    "slowest cable: [[copper_cable]] → [[aluminum_cable]] → [[titanium_cable]] → [[superconductor_cable]].\n"
    "Generators push FE into the network; machines and cells take it. A [[wrench]] sets which face of a cell is the output."),
    "Energía y cables", (
    "Las máquinas funcionan con **FE**. Los cables unen todo lo que tocan en una red, que va al ritmo de su cable más "
    "lento: [[copper_cable]] → [[aluminum_cable]] → [[titanium_cable]] → [[superconductor_cable]].\n"
    "Los generadores empujan FE a la red; máquinas y celdas lo toman. La [[wrench]] elige la cara de salida de una celda."))
entry("generators", "Generators", (
    "- [[combustion_generator]]: furnace fuel, 40 FE/t.\n- [[solar_panel]]: 8 FE/t under open sky by day.\n"
    "- [[geothermal_generator]]: 24 FE/t per touching lava source, which is never used up.\n"
    "- [[magmatic_generator]]: burns lava buckets and magma blocks.\n- [[biogas_generator]]: crops, leaves and rotten "
    "flesh into biogas.\nMore: [[@steam]], [[@wind_turbine]], [[@solar_rtg]], [[@fission_reactor]], [[@tokamak]], "
    "[[@dyson_receiver]]."),
    "Generadores", (
    "- [[combustion_generator]]: combustible de horno, 40 FE/t.\n- [[solar_panel]]: 8 FE/t a cielo abierto de día.\n"
    "- [[geothermal_generator]]: 24 FE/t por cada fuente de lava que toque, que nunca se gasta.\n"
    "- [[magmatic_generator]]: quema cubos de lava y bloques de magma.\n- [[biogas_generator]]: cultivos, hojas y carne "
    "podrida en biogás.\nMás: [[@steam]], [[@wind_turbine]], [[@solar_rtg]], [[@fission_reactor]], [[@tokamak]], "
    "[[@dyson_receiver]]."))
entry("steam", "Steam", (
    "The [[steam_engine]] heats its own boiler: from 100 °C steam turns its flywheel (needs water). A [[boiler]] boils "
    "water into steam for engines and turbines; the [[steam_turbine]] turns a reactor's (or many boilers') steam into "
    "a lot of FE once spun up. The [[kinetic_dynamo]] turns a water wheel's rotation into FE."),
    "Vapor", (
    "La [[steam_engine]] calienta su propia caldera: desde 100 °C el vapor mueve su volante (necesita agua). Una "
    "[[boiler]] hierve agua en vapor para motores y turbinas; la [[steam_turbine]] convierte el vapor de un reactor (o de "
    "muchas calderas) en mucho FE una vez embalada. La [[kinetic_dynamo]] convierte el giro de una rueda hidráulica en FE."))
entry("wind_turbine", "Wind Turbine", (
    "The [[wind_turbine]] nacelle sits on a mast of at least **4** [[turbine_mast|Turbine Masts]]. Its 5×5 rotor disc "
    "in front must be clear. More power the higher it stands above sea level, more in rain and thunderstorms.\n"
    "The mast carries the power down: it comes out into the cable, cell or machine **under the foot of the mast**."),
    "Aerogenerador", (
    "La góndola del [[wind_turbine]] va sobre un mástil de al menos **4** [[turbine_mast|mástiles]]. El disco de 5×5 del "
    "rotor delante debe estar libre. Más energía cuanto más alto sobre el nivel del mar, y más con lluvia y tormenta.\n"
    "El mástil baja la energía: sale al cable, celda o máquina **bajo el pie del mástil**."))
entry("solar_rtg", "Solar Array and RTG", (
    "The [[solar_array]] is six solar panels' worth in daylight, half in rain and 1.5× in airless space. The [[rtg]] "
    "burns a [[radioisotope_pellet]] for an hour of steady power: day, night, in orbit."),
    "Matriz solar y RTG", (
    "La [[solar_array]] equivale a seis paneles de día, la mitad con lluvia y 1,5 veces en el vacío. El [[rtg]] usa una "
    "[[radioisotope_pellet]] para una hora de energía constante: de día, de noche y en órbita."))
entry("energy_storage", "Energy Storage", (
    "[[energy_cell]] → [[advanced_energy_cell]] (4×) → [[industrial_energy_cell]] (16×) → [[quantum_energy_cell]] (64×). "
    "Power leaves through the lightning-bolt face; the slot charges tools."),
    "Almacenamiento de energía", (
    "[[energy_cell]] → [[advanced_energy_cell]] (4×) → [[industrial_energy_cell]] (16×) → [[quantum_energy_cell]] (64×). "
    "La energía sale por la cara del rayo; la ranura carga herramientas."))

# ---- Fluids
entry("fluid_basics", "Pipes, Tanks and Pumps", (
    "[[bronze_fluid_pipe|Fluid pipes]] carry liquids and gases. Wrench a pipe face (or use the pipe's screen) to **pump "
    "out** of the tank or machine there; other faces push in. [[bronze_fluid_tank|Tanks]] hold one fluid and keep it "
    "when broken; liquids settle into a tank below, gases rise into one above. The [[pump]] empties pools from the "
    "farthest source first (water sources stay)."),
    "Tuberías, tanques y bombas", (
    "Las [[bronze_fluid_pipe|tuberías]] llevan líquidos y gases. Usa la llave en una cara (o la pantalla de la tubería) "
    "para **extraer** del tanque o máquina de ahí; las demás caras empujan. Los [[bronze_fluid_tank|tanques]] guardan un "
    "fluido y lo conservan al romperlos; los líquidos bajan al tanque de abajo y los gases suben al de arriba. La [[pump]] "
    "vacía estanques empezando por la fuente más lejana (el agua no se gasta)."))
entry("fluid_tiers", "Better Pipes and Tanks", (
    "Steel and titanium pipes move more mB/s per face; steel and titanium tanks hold more buckets. Mix freely."),
    "Mejores tuberías y tanques", (
    "Las tuberías de acero y titanio mueven más mB/s por cara; los tanques de acero y titanio guardan más cubos. "
    "Se pueden mezclar."))
entry("oil_products", "Oil Products", (
    "Diesel burns in the [[diesel_generator]] (300 FE/t for 1 mB/t). Rocket fuel goes to the [[fuelling_port]] for "
    "rockets and shuttles. [[plastic]] builds electronics; [[tar]] makes [[asphalt]] (fast to walk on)."),
    "Derivados del petróleo", (
    "El diésel se quema en el [[diesel_generator]] (300 FE/t por 1 mB/t). El combustible de cohete va al [[fuelling_port]] "
    "para cohetes y lanzaderas. El [[plastic]] sirve para electrónica; el [[tar]] hace [[asphalt]] (rápido para caminar)."))

# ---- Logistics
entry("item_pipes", "Item Pipes", (
    "Wrench a pipe face touching an inventory to **extract** from it (or right-click the pipe with an empty hand); "
    "items travel to the other inventories on the pipe. Bronze → steel → aluminium → titanium move more items per second."),
    "Tuberías de objetos", (
    "Usa la llave en la cara de una tubería que toca un inventario para **extraer** de él (o clic derecho con la mano "
    "vacía); los objetos van a los demás inventarios. Bronce → acero → aluminio → titanio mueven más por segundo."))
entry("crates", "Crates", (
    "[[wooden_crate|Crates]] keep their contents when broken: move a whole chest of stuff in one item."),
    "Cajones", (
    "Los [[wooden_crate|cajones]] conservan su contenido al romperlos: mueve un cofre entero en un solo objeto."))
entry("storage_network", "Storage Network", (
    "One [[storage_controller]] powers the network; [[storage_drive|drives]] hold up to 4 [[storage_cell_1k|cells]]; a "
    "[[storage_terminal]] searches, takes and stores everything; a [[storage_interface]] lets pipes and hoppers in. "
    "Join them with [[storage_cable]]."),
    "Red de almacenamiento", (
    "Un [[storage_controller]] alimenta la red; las [[storage_drive|unidades]] llevan hasta 4 [[storage_cell_1k|celdas]]; "
    "una [[storage_terminal]] busca, saca y guarda todo; una [[storage_interface]] deja entrar tuberías y tolvas. Únelos con "
    "[[storage_cable]]."))
entry("wireless", "Wireless Terminal", (
    "Sneak-use the [[wireless_terminal]] on a Storage Terminal to link it. With your team's Uplink Satellite overhead "
    "it works anywhere in the dimension."),
    "Terminal inalámbrica", (
    "Agáchate y usa la [[wireless_terminal]] sobre una terminal para enlazarla. Con el satélite de enlace de tu equipo "
    "funciona en toda la dimensión."))

# ---- Nuclear
entry("uranium", "Uranium and Fuel", (
    "Raw uranium smelts to [[uranium_ingot|uranium]]; the [[centrifuge]] enriches 3 uranium dust into 1 "
    "[[enriched_uranium]] (+2 depleted). Assemble enriched uranium into [[fuel_rod|Fuel Rods]].\n"
    "Uranium is radioactive: read [[@radiation]]."),
    "Uranio y combustible", (
    "El uranio en bruto se funde en [[uranium_ingot|uranio]]; la [[centrifuge]] enriquece 3 polvos en 1 "
    "[[enriched_uranium]] (+2 empobrecidos). Ensambla el uranio enriquecido en [[fuel_rod|barras de combustible]].\n"
    "El uranio es radiactivo: lee [[@radiation]]."))
entry("fission_reactor", "Fission Reactor", (
    "A **closed box from 3×3×3 to 7×7×7**. The walls are [[reactor_casing]], [[reactor_glass]], ports and exactly one "
    "[[reactor_controller]] (in a wall, facing out). Inside: [[reactor_fuel_channel|Fuel Channels]], "
    "[[reactor_control_rod|Control Rods]] or air, nothing else.\n"
    "# Ports\n- [[reactor_access_port|Access Port]]: fuel rods and coolant in, spent rods out (pipes, hoppers).\n"
    "- [[reactor_coolant_port|Coolant Port]]: water sources touching it, piped water; gives steam to turbines.\n"
    "- [[reactor_power_port|Power Port]]: the FE comes out here.\n- [[reactor_redstone_port|Redstone Port]]: a signal "
    "SCRAMs; a comparator reads the temperature.\n"
    "# Layout\nOne fuel rod per channel. Channels that touch run hotter and burn better; **one control rod per four "
    "channels** gives full control. The recommended 5×5×5 on the right packs 21 channels in columns with 6 rods. "
    "Use the variant buttons for 3×3×3 and 7×7×7.\nThen read [[@reactor_heat]]."),
    "Reactor de fisión", (
    "Una **caja cerrada de 3×3×3 a 7×7×7**. Las paredes son [[reactor_casing]], [[reactor_glass]], puertos y exactamente "
    "un [[reactor_controller]] (en una pared, mirando afuera). Dentro: [[reactor_fuel_channel|canales]], "
    "[[reactor_control_rod|barras de control]] o aire, nada más.\n"
    "# Puertos\n- [[reactor_access_port|De acceso]]: entran barras y refrigerante, salen las gastadas (tuberías, tolvas).\n"
    "- [[reactor_coolant_port|De refrigerante]]: fuentes de agua que lo toquen, agua entubada; da vapor a las turbinas.\n"
    "- [[reactor_power_port|De energía]]: por aquí sale el FE.\n- [[reactor_redstone_port|De redstone]]: una señal hace "
    "SCRAM; un comparador lee la temperatura.\n"
    "# Disposición\nUna barra de combustible por canal. Los canales que se tocan se calientan más y queman mejor; **una "
    "barra de control por cada cuatro canales** da control total. El 5×5×5 recomendado de la derecha junta 21 canales en "
    "columnas con 6 barras. Usa los botones de variante para 3×3×3 y 7×7×7.\nDespués lee [[@reactor_heat]]."))
entry("reactor_heat", "Reactor Heat and Coolant", (
    "# Heat\nEach loaded rod makes heat, times the reactivity (lower with the control rods in) and times the "
    "neighbour bonus (+30% per touching channel on average).\n"
    "# Cooling and power\nAbove **100 °C** the coolant boils: the walls carry away heat in proportion to the reactor's "
    "**volume** and how far above 100 °C it is, using 1 mB of coolant per 4 heat, and every unit of heat carried "
    "away becomes FE. So a reactor settles where cooling matches heating, **as long as the coolant lasts**.\n"
    "Coolant: water buckets, ice, packed ice, blue ice, [[coolant_cell|Coolant Cells]], or water source blocks touching "
    "a Coolant Port. With a [[steam_turbine]] drawing steam it makes steam instead of FE.\n"
    "# Danger\nWithout coolant the heat has nowhere to go: alarm, then **meltdown** (explosion, corium, radiation). "
    "**SCRAM** (screen button, redstone on the controller or a Redstone Port) drops every rod in at once.\n"
    "Start with the rods fully in, watch the temperature, pull them out slowly."),
    "Calor y refrigeración del reactor", (
    "# Calor\nCada barra cargada produce calor, por la reactividad (menor con las barras de control dentro) y por la "
    "bonificación de vecinos (+30% por canal vecino de media).\n"
    "# Refrigeración y energía\nPor encima de **100 °C** el refrigerante hierve: las paredes se llevan calor en proporción "
    "al **volumen** del reactor y a cuánto supera los 100 °C, gastando 1 mB de refrigerante por cada 4 de calor, y cada "
    "unidad de calor que sale se vuelve FE. Así el reactor se estabiliza donde la refrigeración iguala al calor, **mientras "
    "dure el refrigerante**.\nRefrigerante: cubos de agua, hielo, hielo compacto, hielo azul, [[coolant_cell|celdas de "
    "refrigerante]] o fuentes de agua junto a un puerto de refrigerante. Si una [[steam_turbine]] toma vapor, produce vapor "
    "en vez de FE.\n# Peligro\nSin refrigerante el calor no tiene salida: alarma y luego **fusión del núcleo** (explosión, "
    "corio, radiación). El **SCRAM** (botón, redstone en el controlador o en un puerto de redstone) mete todas las barras "
    "a la vez.\nEmpieza con las barras dentro, vigila la temperatura y sácalas despacio."))
entry("radiation", "Radiation", (
    "Uranium, spent rods, waste and corium irradiate whoever carries or stands near them. The [[geiger_counter]] clicks "
    "faster near radiation and shows your dose. Hazmat pieces each stop a quarter; the full set stops it all. Store hot "
    "items in a [[waste_barrel]]: nothing inside irradiates anyone."),
    "Radiación", (
    "El uranio, las barras gastadas, los residuos y el corio irradian a quien los lleva o está cerca. El [[geiger_counter]] "
    "suena más rápido cerca de la radiación y muestra tu dosis. Cada pieza hazmat para un cuarto; el conjunto, toda. "
    "Guarda lo radiactivo en un [[waste_barrel]]: lo de dentro no irradia a nadie."))
entry("waste", "Spent Fuel and Waste", (
    "A rod is spent after its life at full power and comes out as a Depleted Fuel Rod, very radioactive. Keep it in a "
    "[[waste_barrel]] or reprocess it in the [[centrifuge]] into nuclear waste and [[radioisotope_pellet|RTG pellets]]. "
    "If the reactor has no room for a spent rod it SCRAMs itself."),
    "Combustible gastado y residuos", (
    "Una barra se agota tras su vida a plena potencia y sale como barra empobrecida, muy radiactiva. Guárdala en un "
    "[[waste_barrel]] o reprocésala en la [[centrifuge]] en residuos y [[radioisotope_pellet|pastillas para RTG]]. Si el "
    "reactor no tiene sitio para una barra gastada, hace SCRAM solo."))

# ---- Space
entry("rockets", "Going to Orbit", (
    "Mount a [[crew_capsule]] on the [[@launch_pad]], fuel it and Board from the Launch Controller. **Wear a full "
    "Astronaut Suit**: there is no air up there. Use a [[return_pod]] in orbit to come back down over your launch site.\n"
    "# Arriving\nYou come down at your team's station above the pad, if there is one. If nothing is built up there, "
    "your capsule stays in orbit as a small floating **pod** with you inside (it has air): sneak to climb out and stand "
    "on it, use it to climb back in, and use it from inside to go home (every Return Pod works like this in orbit). There is no free platform: build out from the pod, or "
    "launch a [[station_kit]] first (see Space Stations)."),
    "Ir a la órbita", (
    "Monta una [[crew_capsule]] en la [[@launch_pad]], cárgale combustible y súbete desde el controlador. **Ponte el traje "
    "de astronauta completo**: allí no hay aire. Usa una [[return_pod]] en órbita para volver sobre tu sitio de lanzamiento.\n"
    "# Llegada\nLlegas a la estación de tu equipo sobre la plataforma, si la hay. Si allí no hay nada construido, tu "
    "cápsula se queda en órbita como una pequeña **cápsula flotante** contigo dentro (tiene aire): agáchate para salir y "
    "quedarte de pie encima, úsala para volver a entrar y úsala desde dentro para volver a casa (todas las cápsulas de "
    "retorno funcionan así en órbita). No hay plataforma gratis: "
    "construye desde la cápsula o lanza antes un [[station_kit]] (ver Estaciones espaciales)."))
entry("suits_oxygen", "Suits and Oxygen", (
    "Wear all four Astronaut Suit pieces to breathe in space; the chest piece holds the air. Fill it in an "
    "[[oxygen_compressor]] (in its slot, or standing next to it) or with an [[oxygen_cell]] anywhere. The HUD shows how "
    "much air is left."),
    "Trajes y oxígeno", (
    "Lleva las cuatro piezas del traje de astronauta para respirar en el espacio; el pecho guarda el aire. Llénalo en un "
    "[[oxygen_compressor]] (en su ranura o de pie a su lado) o con una [[oxygen_cell]] en cualquier sitio. El HUD muestra "
    "el aire que queda."))
entry("sealed_rooms", "Sealed Rooms", (
    "An [[oxygen_sealer]] fills the sealed room around it with air: helmets off inside. Walls, floor and ceiling of full "
    "blocks, glass, slabs, stairs or closed [[airlock_door|Airlocks]]. A hole lets the air out, with a hiss and a warning. "
    "An [[air_vent]] does the same for small rooms."),
    "Salas selladas", (
    "Un [[oxygen_sealer]] llena de aire la sala sellada a su alrededor: sin casco dentro. Paredes, suelo y techo de bloques "
    "completos, vidrio, losas, escaleras o [[airlock_door|esclusas]] cerradas. Un agujero deja escapar el aire, con un "
    "silbido y un aviso. Un [[air_vent]] hace lo mismo en salas pequeñas."))
entry("stations", "Space Stations", (
    "A [[station_core]] claims the station around it for your team (others can't break blocks there) and reports "
    "modules, power and air. [[docking_port|Docking Ports]] hold shuttles.\n"
    "# Sending a station up\nCraft a [[station_kit]], mount it on the [[@launch_pad]] and launch it from the Overworld: "
    "it unfolds in orbit straight above the pad (y 100) as a sealed 7×7 module with a Station Core claimed for your team, "
    "a charged Oxygen Sealer, windows, an Airlock, a porch and a Return Pod. The space there must be empty, or the "
    "launch is refused. Crew Capsules and Ascent Modules launched from that pad then land inside it.\n"
    "# Shipping materials\nLoad a [[cargo_pod]] (right-click, 27 slots, or mount it empty and let hoppers or pipes "
    "fill it through the Launch Controller) and launch it: it is unloaded into the **cargo hold** of your team's Station "
    "Core above the pad (sneak-use the core with an empty hand; pipes work too). Without a station there it won't launch.\n"
    "# Magnetic Boots\n[[magnetic_boots]] hold you to the deck at normal gravity, and in low gravity let you walk on "
    "walls and ceilings: see **Walking on Walls**."),
    "Estaciones espaciales", (
    "Un [[station_core]] reclama la estación a su alrededor para tu equipo (otros no pueden romper bloques allí) e informa "
    "de módulos, energía y aire. Los [[docking_port|puertos de atraque]] sujetan lanzaderas.\n"
    "# Enviar una estación\nFabrica un [[station_kit]], móntalo en la [[@launch_pad]] y lánzalo desde el mundo normal: se "
    "despliega en órbita justo encima de la plataforma (y 100) como un módulo sellado de 7×7 con un núcleo de estación de "
    "tu equipo, un sellador de oxígeno cargado, ventanas, una esclusa, un porche y una cápsula de retorno. Ese espacio "
    "debe estar vacío o el lanzamiento se rechaza. Las cápsulas tripuladas y módulos de ascenso lanzados desde esa "
    "plataforma llegan luego dentro.\n"
    "# Enviar materiales\nCarga una [[cargo_pod]] (clic derecho, 27 huecos, o móntala vacía y deja que tolvas o tuberías "
    "la llenen por el controlador de lanzamiento) y lánzala: se descarga en la **bodega** del núcleo de estación de tu "
    "equipo sobre la plataforma (úsalo agachado con la mano vacía; también con tuberías). Sin estación allí no despega.\n"
    "# Botas magnéticas\nLas [[magnetic_boots]] te sujetan a la cubierta con gravedad normal y en baja gravedad te "
    "dejan caminar por paredes y techos: mira **Caminar por las paredes**."))
entry("magnetic_boots", "Walking on Walls", (
    "In low gravity (orbit, the Moon, Mars, Io) [[magnetic_boots]] turn **your own gravity**: whatever surface the soles "
    "stand on becomes your floor. They hold you to it at normal gravity, so walking, jumping and building feel like home.\n"
    "# Getting on a wall\nWalk into a wall (at least two blocks high) and you step onto it: the view swings round, the "
    "wall is now the floor and the room is lying on its side. Walk up it into the ceiling and the ceiling becomes your "
    "floor; walk down a wall back onto the deck and you are the right way up again. Jump into a ceiling to flip onto it.\n"
    "# Up there\nEverything turns with you: the mouse looks around your new up, WASD walks along the surface, jumping "
    "pushes off it, you place, break and use blocks from where your eyes are, and other players see you standing on the wall. "
    "You walk through doorways and over small steps as usual; walls bending outwards (an outside corner) can't be followed.\n"
    "# Letting go\nSneak to switch the magnets off and drift down. You also let go when the boots come off, gravity is "
    "normal, you leave every surface for a moment, or you fly, ride, swim, glide or sleep. Falls are forgiven while the "
    "boots hold you.\n"
    "# Config\n**magneticBootsRotateGravity** (space server config, on by default). Off: the boots climb walls and hang from "
    "ceilings with an upright view instead (forward climbs, back climbs down)."),
    "Caminar por las paredes", (
    "En baja gravedad (órbita, la Luna, Marte, Ío) las [[magnetic_boots]] giran **tu propia gravedad**: la superficie que "
    "pisan las suelas pasa a ser tu suelo. Te sujetan a ella con gravedad normal, así que caminar, saltar y construir es "
    "como en casa.\n"
    "# Subir a una pared\nCamina contra una pared (de al menos dos bloques de alto) y te subes a ella: la vista gira, la "
    "pared es ahora el suelo y la sala queda de lado. Sube por ella hasta el techo y el techo será tu suelo; baja por una "
    "pared hasta la cubierta y vuelves a estar derecho. Salta contra un techo para darte la vuelta sobre él.\n"
    "# Allí arriba\nTodo gira contigo: el ratón mira alrededor de tu nuevo arriba, WASD camina por la superficie, saltar te "
    "separa de ella, colocas, rompes y usas bloques desde tus ojos, y los demás jugadores te ven de pie en la pared. Pasas "
    "por puertas y pequeños escalones como siempre; las esquinas exteriores (paredes que se doblan hacia fuera) no se pueden "
    "seguir.\n"
    "# Soltarse\nAgáchate para apagar los imanes y bajar flotando. También te sueltas si te quitas las botas, la gravedad es "
    "normal, te alejas de toda superficie un momento, o vuelas, montas, nadas, planeas o duermes. Mientras las botas te "
    "sujetan no hay daño por caída.\n"
    "# Configuración\n**magneticBootsRotateGravity** (configuración espacial del servidor, activada por defecto). Desactivada: "
    "las botas trepan paredes y cuelgan de techos con la vista derecha (adelante sube, atrás baja)."))
entry("shuttle", "Orbital Shuttle", (
    "The [[shuttle]] carries two and burns Rocket Fuel: climb above the sky to reach orbit, dive back to re-enter. The "
    "cabin is sealed. Refuel at a [[fuelling_port]]; an [[ion_drive]] in the hold halves fuel and time; the "
    "[[star_chart]] shows planets and stations.\n"
    "Steer with the keys only (W/S thrust, A/D turn, Space/Shift up and down): the mouse looks around freely, in first "
    "and third person, without turning the ship. The same goes for the sea ships."),
    "Lanzadera orbital", (
    "La [[shuttle]] lleva a dos y quema combustible de cohete: sube por encima del cielo para llegar a la órbita y "
    "desciende para reentrar. La cabina está sellada. Reposta en un [[fuelling_port]]; un [[ion_drive]] en la bodega reduce "
    "a la mitad combustible y tiempo; la [[star_chart]] muestra planetas y estaciones.\n"
    "Se pilota solo con teclas (W/S empuje, A/D girar, Espacio/Mayús subir y bajar): el ratón mira libremente, en primera "
    "y tercera persona, sin girar la nave. Lo mismo vale para los barcos."))
entry("planets", "Planets", (
    "The **Moon** (Helium-3 regolith), **Mars** (ice, hematite) and **Io** (sulfur, scorching: sew [[thermal_lining]] into "
    "your suit). The [[fuel_synthesizer]] makes fuel where there is none; an [[ascent_module]] takes you back to orbit; "
    "a lit [[distress_beacon]] calls your team."),
    "Planetas", (
    "La **Luna** (regolito con helio-3), **Marte** (hielo, hematita) e **Ío** (azufre, abrasador: cose un "
    "[[thermal_lining]] al traje). El [[fuel_synthesizer]] fabrica combustible donde no lo hay; un [[ascent_module]] te "
    "devuelve a la órbita; una [[distress_beacon]] encendida llama a tu equipo."))
entry("jetpacks", "Jetpacks", (
    "Wear a [[electric_jetpack]] and hold jump to fly; H (or sneak-use) toggles hover. The [[advanced_jetpack]] and the "
    "[[jet_suit]] fly faster and longer. Charge them in a Charger or Energy Cell."),
    "Mochilas propulsoras", (
    "Ponte una [[electric_jetpack]] y mantén saltar para volar; H (o usar agachado) activa el modo estático. La "
    "[[advanced_jetpack]] y el [[jet_suit]] vuelan más rápido y más tiempo. Cárgalos en un cargador o una celda."))

# ---- Dyson
entry("dyson_overview", "The Dyson Project", (
    "Every [[dyson_collector|Solar Collector]] you fire into solar orbit joins your team's **swarm** around the sun. "
    "The more collectors, the more power every [[@dyson_receiver]] beams down, and the more of the sun you will see "
    "wrapped in panels. Milestones at 10, 25, 50 and 100 collectors.\nThe [[dyson_monitor]] shows the swarm as a hologram."),
    "El proyecto Dyson", (
    "Cada [[dyson_collector|colector solar]] que disparas a la órbita solar se une al **enjambre** de tu equipo. Cuantos más "
    "colectores, más energía envía cada [[@dyson_receiver]], y más verás el sol cubierto de paneles. Hitos en 10, 25, 50 y "
    "100 colectores.\nEl [[dyson_monitor]] muestra el enjambre como holograma."))
entry("mass_driver", "Mass Driver", (
    "The [[mass_driver]] breech with **4** [[mass_driver_rail|Mass Driver Rails]] stacked on top and open sky above. Load "
    "Solar Collectors (by hand, hopper or pipe), feed it energy from cables on any side; each shot costs a large charge "
    "and the rails light up as it charges. A Launch Pad can also carry one collector per rocket."),
    "Lanzador de masas", (
    "La recámara del [[mass_driver]] con **4** [[mass_driver_rail|rieles]] apilados encima y cielo abierto. Carga colectores "
    "(a mano, tolva o tubería) y dale energía por cables en cualquier lado; cada disparo cuesta una gran carga y los rieles "
    "se iluminan al cargarse. Una plataforma de lanzamiento también lleva un colector por cohete."))
entry("dyson_receiver", "Dyson Receiver", (
    "A [[dyson_receiver]] surrounded by 8 [[dyson_receiver_array|Receiver Arrays]] (a 3×3 rectenna). It needs open sky "
    "and daylight on a planet (rain scatters some light); in space it is always on. FE per collector is shared by your "
    "receivers and comes out of the bottom of the centre block."),
    "Receptor Dyson", (
    "Un [[dyson_receiver]] rodeado de 8 [[dyson_receiver_array|antenas]] (una rectena de 3×3). Necesita cielo abierto y luz "
    "de día en un planeta (la lluvia dispersa parte); en el espacio siempre funciona. El FE por colector se reparte entre "
    "tus receptores y sale por abajo del bloque central."))

# ---- Ender
entry("ender_anchor", "Ender Anchor", (
    "A two-block stasis chamber that keeps the chunks around it loaded while an ender pearl floats inside. Place it, then "
    "right-click with an [[minecraft:ender_pearl|Ender Pearl]] (or put one in its slot). Breaking it loses the pearl."),
    "Ancla de Ender", (
    "Una cámara de estasis de dos bloques que mantiene cargados los chunks de alrededor mientras una perla de ender flota "
    "dentro. Colócala y haz clic derecho con una [[minecraft:ender_pearl|perla de ender]] (o ponla en su ranura). Al "
    "romperla se pierde la perla."))
entry("recall", "Recall Charm", (
    "Sneak-use a [[recall_charm]] on an [[ender_beacon]] to link it. Hold use for a few seconds to be pulled home; "
    "taking damage breaks the channel. Each recall uses up the beacon's pearl. If the beacon was destroyed or is empty "
    "the charm says so and keeps the link (marked broken).\nThe [[factory_phone]]'s Recall app does the same without a "
    "charm, for every beacon linked to the phone (see [[@phone]])."),
    "Amuleto de regreso", (
    "Agáchate y usa un [[recall_charm]] sobre una [[ender_beacon]] para enlazarlos. Mantén usar unos segundos para volver "
    "a casa; recibir daño corta el canal. Cada regreso gasta la perla de la baliza. Si la baliza fue destruida o está "
    "vacía, el amuleto lo dice y conserva el enlace (marcado como roto).\nLa app Regreso del [[factory_phone]] hace lo "
    "mismo sin amuleto, para cada baliza enlazada al teléfono (mira [[@phone]])."))
entry("links", "Cross-Dimension Links", (
    "An [[ender_link]] moves items and fluids to every other endpoint on its **channel**: within one dimension or "
    "Overworld ↔ Nether, free to run. A [[quantum_entangler]] also moves energy and reaches any two dimensions, orbit and "
    "planets included, for FE.\n# Setting up\n- Right-click: pick or create a channel (your team's, or public).\n"
    "- Wrench a face to cycle **off / send / receive** for each resource.\n"
    "- A tuned endpoint keeps its own chunk loaded, so the far end keeps pumping."),
    "Enlaces interdimensionales", (
    "Un [[ender_link]] mueve objetos y fluidos a todos los demás extremos de su **canal**: dentro de una dimensión o "
    "Superficie ↔ Nether, gratis. Un [[quantum_entangler]] también mueve energía y conecta dos dimensiones cualesquiera, "
    "órbita y planetas incluidos, a cambio de FE.\n# Configuración\n- Clic derecho: elige o crea un canal (de tu equipo o "
    "público).\n- Usa la llave en una cara para alternar **apagado / enviar / recibir** por recurso.\n"
    "- Un extremo sintonizado mantiene cargado su chunk, así el otro lado sigue funcionando."))

# ---- Ships
entry("sailing", "Sailing Ship", (
    "The [[bronze_cog]] seats two and holds 27 stacks. Wind drives it: fastest with the wind on the beam, slow straight "
    "into it; storms blow harder. Right-click water to launch it. W/S throttle, A/D steer, Shift to leave."),
    "Velero", (
    "El [[bronze_cog]] lleva a dos y 27 pilas de carga. Lo mueve el viento: más rápido con viento de costado, lento contra "
    "el viento; las tormentas soplan más. Clic derecho en el agua para botarlo. W/S acelerar, A/D girar, Mayús para bajar."))
entry("motor_ship", "Motor Ship", (
    "The [[motor_ship]] seats four, holds 54 stacks, has a headlight and a horn. It runs on furnace fuel or FE."),
    "Barco a motor", (
    "El [[motor_ship]] lleva a cuatro, 54 pilas de carga, faro y bocina. Funciona con combustible de horno o FE."))
entry("orbital_shuttle", "Shuttle Flight", (
    "See [[@shuttle]]. W/S thrust, A/D yaw, Space up, Shift down, K opens the hatch. Ease down onto a "
    "[[docking_port]] to dock and refuel."),
    "Vuelo en lanzadera", (
    "Mira [[@shuttle]]. W/S empuje, A/D giro, Espacio subir, Mayús bajar, K abre la escotilla. Desciende con cuidado sobre "
    "un [[docking_port]] para atracar y repostar."))

# ---- Trains
entry("steam_locomotive", "Steam Locomotive", (
    "The [[steam_locomotive]] runs on ordinary minecart rails: straights, curves, slopes, junctions, powered, detector "
    "and activator rails. Right-click a rail to set it down (its front toward where you look) and right-click it to "
    "climb into the cab.\n# Driving\n**W/S** move the throttle lever (it stays where you leave it; past zero is "
    "reverse), **Space** brakes, **H** blows the whistle, **J** lights the lamp, **E** opens the cab, Shift gets off. "
    "The mouse looks around freely. Speed builds up gradually, more slowly the heavier the train.\n# Fuel and water\n"
    "The firebox burns furnace fuel from the four bunker slots (hoppers above the rails can top them up) and boils "
    "water from a 10,000 mB tank: fill it with water buckets or at a [[train_station]]. Pressure has to build before it "
    "pulls; out of water or fuel it coasts. Up to 36 km/h.\n# Rails\nUnpowered powered rails stop it, powered ones "
    "speed up slow trains, like minecarts. A locomotive left with its throttle open keeps going on its own."),
    "Locomotora de vapor", (
    "La [[steam_locomotive]] va por rieles de vagoneta normales: rectas, curvas, pendientes, cruces y rieles propulsores, "
    "detectores y activadores. Clic derecho en un riel para colocarla (el frente hacia donde miras) y clic derecho sobre "
    "ella para subir a la cabina.\n# Conducir\n**W/S** mueven el regulador (se queda donde lo dejas; pasado el cero es "
    "marcha atrás), **Espacio** frena, **H** hace sonar el silbato, **J** enciende el farol, **E** abre la cabina, Mayús "
    "para bajar. El ratón mira libremente. La velocidad sube poco a poco, más despacio cuanto más pesa el tren.\n"
    "# Combustible y agua\nEl hogar quema combustible de horno de las cuatro casillas de la carbonera (las tolvas sobre "
    "los rieles pueden rellenarlas) y hierve el agua de un tanque de 10.000 mB: llénalo con cubos de agua o en una "
    "[[train_station]]. Hay que levantar presión antes de tirar; sin agua o sin fuego avanza por inercia. Hasta 36 km/h.\n"
    "# Rieles\nLos rieles propulsores apagados la detienen y los encendidos aceleran trenes lentos, como a las vagonetas. "
    "Una locomotora con el regulador abierto sigue sola."))
entry("wagons", "Wagons and Coupling", (
    "Wagons go on rails like minecarts. Join them with a [[coupler]]: right-click one vehicle, then the one next to it; "
    "sneak-right-click a vehicle to uncouple the end you clicked. A coupled train keeps its spacing on curves and slopes "
    "and follows the locomotive (pushing works too); it never derails. A coupling breaks only if the track under a "
    "wagon disappears or one of them is destroyed. Trains have at most 12 vehicles (config).\n"
    "- [[passenger_car]]: four seats.\n- [[cargo_wagon]]: 54 slots; hoppers above or below the rails load and unload "
    "it like a chest minecart.\n- [[tank_wagon]]: 32,000 mB of any fluid, filled and emptied at a station or by bucket.\n"
    "- [[hopper_wagon]]: open top, catches items dropped into it, dumps its load on a powered activator rail.\n"
    "Breaking a vehicle drops it with its cargo, tank and charge inside."),
    "Vagones y enganches", (
    "Los vagones van por los rieles como vagonetas. Únelos con un [[coupler]]: clic derecho en un vehículo y luego en el "
    "de al lado; agáchate y haz clic derecho para desenganchar el extremo que tocaste. Un tren enganchado mantiene la "
    "distancia en curvas y pendientes y sigue a la locomotora (también empujando); nunca descarrila. Un enganche solo se "
    "rompe si desaparece la vía bajo un vagón o se destruye uno de ellos. Los trenes tienen como máximo 12 vehículos "
    "(configurable).\n- [[passenger_car]]: cuatro asientos.\n- [[cargo_wagon]]: 54 casillas; las tolvas encima o debajo "
    "de los rieles lo cargan y descargan como una vagoneta con cofre.\n- [[tank_wagon]]: 32.000 mB de cualquier fluido, "
    "se llena y vacía en una estación o con cubos.\n- [[hopper_wagon]]: abierto, recoge los objetos que caen dentro y "
    "descarga sobre un riel activador encendido.\nAl romper un vehículo cae con su carga, su tanque y su energía dentro."))
entry("train_station", "Train Stations", (
    "Place a [[train_station]] next to the rails. A train arriving on the rails beside it brakes and stops (always, only "
    "while the station gets redstone, or never), waits (a set time, or until nothing more moves) and leaves.\n"
    "# Loading\nWhile a train stands there, the station moves items, fluids and FE between the stopped vehicles and the "
    "chests, tanks and batteries touching the station: **Load** fills wagons, locomotive bunkers, water and diesel tanks "
    "and batteries; **Unload** empties wagons and tank wagons. Pipes connected to the station itself reach the docked "
    "vehicles. Its lamp turns green while it holds a train."),
    "Estaciones de tren", (
    "Coloca una [[train_station]] junto a los rieles. Un tren que llega a los rieles de al lado frena y se detiene "
    "(siempre, solo mientras la estación recibe redstone, o nunca), espera (un tiempo fijo o hasta que no se mueva nada "
    "más) y sigue.\n# Carga\nMientras el tren está detenido, la estación mueve objetos, fluidos y FE entre los vehículos "
    "y los cofres, tanques y baterías que la tocan: **Cargar** llena vagones, carboneras, tanques de agua y diésel y "
    "baterías; **Descargar** vacía vagones y cisternas. Las tuberías conectadas a la estación llegan a los vehículos "
    "detenidos. Su luz se pone verde mientras retiene un tren."))
entry("diesel_locomotive", "Diesel-Electric Locomotive", (
    "The [[diesel_locomotive]] drives like the steam engine but pulls harder and reaches 65 km/h. Its traction motors "
    "run from a 200k FE battery that an on-board diesel generator recharges from a 16,000 mB diesel tank. Fill it with "
    "diesel buckets or at a [[train_station]] (which can also charge the battery from an energy cell next to it), or put "
    "a charged item in its power slot. **J** switches the headlight, **H** sounds the horn."),
    "Locomotora diésel-eléctrica", (
    "La [[diesel_locomotive]] se conduce como la de vapor pero tira más fuerte y llega a 65 km/h. Sus motores de tracción "
    "usan una batería de 200k FE que un generador diésel a bordo recarga desde un tanque de 16.000 mB. Llénala con cubos "
    "de diésel o en una [[train_station]] (que también carga la batería desde una celda de energía al lado), o pon un "
    "objeto cargado en su casilla de energía. **J** enciende el faro, **H** toca la bocina."))

# ---- Phone
entry("phone", "Factory Phone", (
    "Right-click the [[factory_phone]] to open it; charge it in a Charger. Sneak-use it on a machine, cable or storage "
    "terminal to link it. Apps: map, storage, team chat, machines (alerts when one stops), power, recall and Dyson. "
    "Away from your base the network apps need your team's [[uplink_satellite]] overhead.\n# Linking with cards\n"
    "Sneak-use a [[link_card]] on anything the phone can watch: machines, generators, reactors, tanks, power cables, "
    "storage terminals, Ender Beacons, Dyson Receivers and Monitors, the orbital consoles, Ender Links… Put the phone "
    "in a [[phone_dock]] and the card in its reader: the link is added and a blank card comes out. The dock lists "
    "every link with a remove button and charges the phone.\n# Recall\nThe Recall app lists every linked Ender Beacon "
    "(swipe or use the arrows) and recalls you without a charm."),
    "Teléfono de fábrica", (
    "Clic derecho con el [[factory_phone]] para abrirlo; cárgalo en un cargador. Agáchate y úsalo sobre una máquina, un "
    "cable o una terminal para enlazarlo. Apps: mapa, almacenamiento, chat de equipo, máquinas (avisa si una se para), "
    "energía, regreso y Dyson. Lejos de la base las apps de red necesitan el [[uplink_satellite]] de tu equipo.\n"
    "# Enlazar con tarjetas\nAgáchate y usa una [[link_card]] sobre lo que el teléfono pueda vigilar: máquinas, "
    "generadores, reactores, tanques, cables, terminales, balizas de ender, receptores y monitores Dyson, consolas "
    "orbitales, enlaces de ender… Pon el teléfono en una [[phone_dock]] y la tarjeta en su lector: se añade el enlace y "
    "sale una tarjeta en blanco. La base lista todos los enlaces con un botón para quitarlos y carga el teléfono.\n"
    "# Regreso\nLa app Regreso lista todas las balizas de ender enlazadas (desliza o usa las flechas) y te lleva sin amuleto."))

# status lines that mention the Manual (they replace the shorter ones written elsewhere)
INCOMPLETE = {
    "status.factoryascent.incomplete": ("Incomplete (%s): see Manual", "Incompleta (%s): ver Manual"),
    "status.factoryascent.fluid.incomplete": ("Incomplete: see the Manual", "Incompleta: ver el Manual"),
    "gui.factoryascent.reactor.incomplete": ("Incomplete: see the Manual", "Incompleto: ver el Manual"),
    "gui.factoryascent.tokamak.incomplete": ("Ring incomplete: see the Manual", "Anillo incompleto: ver el Manual"),
    "message.factoryascent.pad_incomplete": (
        "Launch Pad incomplete: surround the controller with 8 Launch Pad blocks (see the Manual for the layout)",
        "Plataforma incompleta: rodea el controlador con 8 bloques de plataforma (mira el Manual)"),
    "message.factoryascent.dyson_receiver.incomplete": (
        "Dyson Receiver: needs 8 Receiver Arrays around it (see the Manual for the layout)",
        "Receptor Dyson: necesita 8 antenas alrededor (mira el Manual)"),
    "message.factoryascent.mass_driver.incomplete": (
        "needs %s rails stacked on top (see the Manual)", "faltan %s rieles apilados encima (mira el Manual)"),
}


def _java_ids(pattern, file):
    path = JAVA / file
    return re.findall(pattern, path.read_text(encoding="utf-8")) if path.exists() else []


def generate(ctx):
    L = ctx.lang
    ctx.flat_item("factory_manual")
    L(f"item.{MOD}.factory_manual", "Factory Ascent Manual", "Manual de Factory Ascent")
    ctx.shapeless("factory_manual", ["minecraft:book", "minecraft:copper_ingot"], "factory_manual")
    ctx.advancement("stone_manual", "root", "factory_manual", ["factory_manual"], "Read the Manual",
                    "Get the Factory Ascent Manual: guides, recipes and 3D multiblock blueprints",
                    "Lee el manual", "Consigue el Manual de Factory Ascent: guías, recetas y planos 3D de multibloques")
    G = f"guide.{MOD}"
    for key, (en, es) in UI.items():
        L(f"{G}.{key}", en, es)
    for ch, (ten, tes, xen, xes) in CHAPTERS.items():
        L(f"{G}.chapter.{ch}", ten, tes)
        L(f"{G}.chapter.{ch}.text", xen, xes)
        L(f"{G}.chapter.{ch}.short", *SHORT.get(ch, (ten, tes)))
    for mb, (en, short_en, es, short_es) in MB.items():
        L(f"{G}.mb.{mb}", en, es)
        L(f"{G}.mb.{mb}.short", short_en, short_es)
    for key, (en, es) in NOTES.items():
        L(f"{G}.note.{key}", en, es)
    for eid, (ten, xen, tes, xes) in E.items():
        L(f"{G}.entry.{eid}.title", ten, tes)
        L(f"{G}.entry.{eid}.text", xen, xes)
    L(f"key.category.{MOD}.guide", "Factory Ascent: Manual", "Factory Ascent: Manual")
    L(f"key.{MOD}.holo_next", "Hologram: next layer", "Holograma: capa siguiente")
    L(f"key.{MOD}.holo_prev", "Hologram: previous layer", "Holograma: capa anterior")
    L(f"key.{MOD}.holo_clear", "Hologram: remove", "Holograma: quitar")
    # keep the book and the code in step
    for eid in _java_ids(r'e\("([a-z0-9_]+)", ', "GuideEntries.java"):
        if eid not in E:
            print(f"guide.py: entry '{eid}' has no text")
    for ch in _java_ids(r'\b([A-Z]+)\("[a-z0-9_]+", 0x', "GuideEntries.java"):
        if ch.lower() not in CHAPTERS:
            print(f"guide.py: chapter '{ch}' has no text")


def finalize(ctx):
    """After every feature wrote its lang: the 'structure incomplete' lines point at the Manual."""
    for key, (en, es) in INCOMPLETE.items():
        ctx.lang(key, en, es)
