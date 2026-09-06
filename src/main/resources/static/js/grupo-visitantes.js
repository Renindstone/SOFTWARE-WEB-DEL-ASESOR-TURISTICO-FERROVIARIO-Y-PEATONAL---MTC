// RF-18: permite declarar la edad de cada acompanante antes de generar el
// informe. El backend agrupa las edades repetidas y aplica la tarifa que
// corresponde a cada tramo, asi que aqui solo se anaden y quitan campos.
document.addEventListener("DOMContentLoaded", function () {
  var contenedor = document.getElementById("grupo-visitantes");
  var agregar = document.getElementById("agregar-visitante");
  var quitar = document.getElementById("quitar-visitante");
  if (!contenedor || !agregar || !quitar) {
    return;
  }

  // Tope defensivo: el aforo de una zona se agota mucho antes, y un grupo
  // enorme solo serviria para castigar la consulta.
  var MAXIMO_ACOMPANANTES = 15;

  function filas() {
    return contenedor.querySelectorAll(".visitante-fila");
  }

  function actualizarBotones() {
    agregar.disabled = filas().length >= MAXIMO_ACOMPANANTES;
    quitar.disabled = filas().length <= 1;
  }

  agregar.addEventListener("click", function () {
    if (filas().length >= MAXIMO_ACOMPANANTES) {
      return;
    }
    var plantilla = filas()[0].cloneNode(true);
    var campo = plantilla.querySelector("input[name='edades']");
    campo.value = "";
    contenedor.appendChild(plantilla);
    campo.focus();
    actualizarBotones();
  });

  quitar.addEventListener("click", function () {
    var actuales = filas();
    if (actuales.length > 1) {
      contenedor.removeChild(actuales[actuales.length - 1]);
    }
    actualizarBotones();
  });

  actualizarBotones();
});
