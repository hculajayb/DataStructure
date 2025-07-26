package com.example;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class AnalizadorAPI {

    static int totalProcesos = 0;
    static int procesosCompletos = 0;
    static int procesosPendientes = 0;
    static int recursosHerramientas = 0;
    static double eficienciaTotal = 0;
    static int procesosEvaluados = 0;
    static JsonNode procesoMasAntiguo = null;

    public static void main(String[] args) throws Exception {
        // Datos del estudiante
        String nombre = "Henry Culajay";
        String carnet = "7590-15-6649";
        String seccion = "5";

        // Obtener el JSON desde la API compartida
        String endpoint = "https://58o1y6qyic.execute-api.us-east-1.amazonaws.com/default/taskReport";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // Soporte de fechas
        JsonNode response = mapper.readTree(conn.getInputStream());

        // Procesar cada proceso raíz
        for (JsonNode proceso : response.get("procesos")) {
            procesar(proceso);
        }

        double eficienciaPromedio = procesosEvaluados > 0 ? eficienciaTotal / procesosEvaluados : 0.0;

        // Crear resultadoBusqueda
        ObjectNode resultadoBusqueda = mapper.createObjectNode();
        resultadoBusqueda.put("totalProcesos", totalProcesos);
        resultadoBusqueda.put("procesosCompletos", procesosCompletos);
        resultadoBusqueda.put("procesosPendientes", procesosPendientes);
        resultadoBusqueda.put("recursosTipoHerramienta", recursosHerramientas);
        resultadoBusqueda.put("eficienciaPromedio", eficienciaPromedio);

        ObjectNode masAntiguo = mapper.createObjectNode();
        if (procesoMasAntiguo != null) {
            masAntiguo.put("id", procesoMasAntiguo.get("id").asInt());
            masAntiguo.put("nombre", procesoMasAntiguo.get("nombre").asText());
            masAntiguo.put("fechaInicio", procesoMasAntiguo.get("fechaInicio").asText());
        }
        resultadoBusqueda.set("procesoMasAntiguo", masAntiguo);

        // Crear JSON final
        ObjectNode finalJson = mapper.createObjectNode();
        finalJson.put("nombre", nombre);
        finalJson.put("carnet", carnet);
        finalJson.put("seccion", seccion);
        finalJson.set("resultadoBusqueda", resultadoBusqueda);

        // Agregar payload como array con un solo objeto
        ArrayNode payloadArray = mapper.createArrayNode();
        payloadArray.add(response);
        finalJson.set("payload", payloadArray);

        // Guardar en archivo local
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("resultadoEvaluacion.json"), finalJson);
        System.out.println("✅ JSON generado correctamente en resultadoEvaluacion.json");
    }

    private static void procesar(JsonNode proceso) {
        totalProcesos++;

        String estado = proceso.get("estado").asText();
        if ("completo".equalsIgnoreCase(estado)) {
            procesosCompletos++;
        } else {
            procesosPendientes++;
        }

        // Métrica para eficiencia
        if (proceso.has("metricas") && proceso.get("metricas").has("eficiencia")) {
            eficienciaTotal += proceso.get("metricas").get("eficiencia").asDouble();
            procesosEvaluados++;
        }

        // Recursos de tipo herramienta
        JsonNode recursos = proceso.get("recursos");
        if (recursos != null && recursos.isArray()) {
            for (JsonNode recurso : recursos) {
                if ("herramienta".equalsIgnoreCase(recurso.get("tipo").asText())) {
                    recursosHerramientas++;
                }
            }
        }

        // Determinar proceso más antiguo
        String fecha = proceso.get("fechaInicio").asText();
        if (procesoMasAntiguo == null || parseFecha(fecha).isBefore(parseFecha(procesoMasAntiguo.get("fechaInicio").asText()))) {
            procesoMasAntiguo = proceso;
        }

        // Recursividad para hijos
        if (proceso.has("childs") && proceso.get("childs").isArray()) {
            for (JsonNode hijo : proceso.get("childs")) {
                procesar(hijo);
            }
        }
    }

    private static LocalDateTime parseFecha(String fechaIso) {
        return LocalDateTime.parse(fechaIso, DateTimeFormatter.ISO_DATE_TIME);
    }
}
