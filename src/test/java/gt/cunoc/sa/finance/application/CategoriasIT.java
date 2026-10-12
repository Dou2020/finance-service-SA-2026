package gt.cunoc.sa.finance.application;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import gt.cunoc.sa.finance.Escenario;
import gt.cunoc.sa.finance.PruebaDeIntegracion;

/** Catálogo de categorías de gasto: lo administra el SYS_ADMIN, lo lee el BRANCH_ADMIN. */
@PruebaDeIntegracion
class CategoriasIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    Escenario escenario;

    @Test
    void elSysAdminCreaRenombraYDesactiva() throws Exception {
        String nombre = Escenario.codigo("Transmisión ");
        int id = crear(nombre);

        mvc.perform(put("/api/categorias-gasto/" + id).with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(nombre + "  automática ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre + " automática"));

        mvc.perform(patch("/api/categorias-gasto/" + id + "/estado").with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        mvc.perform(get("/api/categorias-gasto").param("activa", "true")
                        .with(Escenario.como(Escenario.adminDe(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(get("/api/categorias-gasto").param("activa", "false")
                        .with(Escenario.como(Escenario.adminDe(UUID.randomUUID()))))
                .andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void unNombreRepetidoEnOtraCapitalizacionDa409() throws Exception {
        String nombre = Escenario.codigo("Frenos de aire ");
        int otra = crear(Escenario.codigo("Otra "));
        crear(nombre);

        mvc.perform(post("/api/categorias-gasto").with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(nombre.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una categoría con ese nombre"));
        mvc.perform(put("/api/categorias-gasto/" + otra).with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(nombre.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una categoría con ese nombre"));
    }

    @Test
    void conGastosNoSeEliminaSoloSeDesactiva() throws Exception {
        int categoria = escenario.categoria();
        escenario.gasto(escenario.sucursal(), categoria);

        mvc.perform(delete("/api/categorias-gasto/" + categoria).with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La categoría tiene gastos registrados: solo se puede desactivar"));
    }

    @Test
    void sinGastosSeElimina() throws Exception {
        int categoria = escenario.categoria();

        mvc.perform(delete("/api/categorias-gasto/" + categoria).with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/categorias-gasto/" + categoria).with(Escenario.como(Escenario.sysAdmin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe esa categoría"));
    }

    @Test
    void soloElSysAdminLaAdministra() throws Exception {
        UUID sucursal = UUID.randomUUID();
        mvc.perform(post("/api/categorias-gasto").with(Escenario.como(Escenario.adminDe(sucursal)))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Escenario.codigo("Vidrios "))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/categorias-gasto").with(Escenario.como(Escenario.cajeroDe(sucursal))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/categorias-gasto")).andExpect(status().isUnauthorized());
    }

    @Test
    void unNombreVacioDa400ConElCampo() throws Exception {
        mvc.perform(post("/api/categorias-gasto").with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json("  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    private int crear(String nombre) throws Exception {
        MvcResult r = mvc.perform(post("/api/categorias-gasto").with(Escenario.como(Escenario.sysAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(nombre)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.activa").value(true))
                .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    private static String json(String nombre) {
        return "{\"nombre\": \"" + nombre + "\"}";
    }
}
