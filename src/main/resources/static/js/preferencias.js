// Los filtros del buscador de zonas se aplican solos al cambiarlos, para que
// el listado se recorte a medida que el turista los va marcando en vez de
// obligarle a pulsar "Aplicar" cada vez. El boton sigue ahi para quien
// navegue con el teclado o tenga el JavaScript desactivado: el formulario
// funciona igual sin este archivo.
document.addEventListener("DOMContentLoaded", function () {
  var formulario = document.getElementById("filtros-zonas");
  if (!formulario) {
    return;
  }

  // "change" y no "input": en el campo de minutos, reenviar en cada pulsacion
  // dispararia una busqueda por cada digito tecleado.
  formulario.addEventListener("change", function (evento) {
    if (evento.target.matches("select, input[type='checkbox'], input[type='number']")) {
      formulario.submit();
    }
  });
});
