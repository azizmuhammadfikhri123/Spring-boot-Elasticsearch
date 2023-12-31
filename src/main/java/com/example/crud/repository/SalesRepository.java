package com.example.crud.repository;

import com.example.crud.models.FilterQuery;
import com.example.crud.models.FilterTerms;
import com.example.crud.models.Sales;
import com.example.crud.util.JerseyRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Repository
public class SalesRepository {
    private final JerseyRequest request = new JerseyRequest();

    public Object getSales(FilterQuery filter) throws Exception{
        ObjectMapper mapper = new ObjectMapper();
        String query = mapper.writeValueAsString(processListTerms(filter));

        Integer size = filter.getSize();
        Integer page = filter.getPage() - 1;
        Integer from = (size * page) < 0 ? 0 : (size * page);
        String body = "{\"from\":" + from + ",\"size\":" + size + ",\"query\":{\"bool\":{\"must\":" + query + "}}}";

        JsonNode queryResult = request.getWithBody("http://192.168.20.90:9200/sales_v2/_search", body).get("hits");

        if(queryResult != null){
            return processMappingGetSales(queryResult);
        }
        return null;
    }

    public Object processListTerms(FilterQuery filter) throws Exception{
        List<Map<String, Object>> listTerms = new ArrayList<Map<String, Object>>();
        for (FilterTerms query : filter.getQuery()) {
            Map<String, Object> terms = new HashMap<>();
            Map<String, Object> field = new HashMap<>();
            Map<String, Object> search = new HashMap<>();

            if (query.getField().equals("search")){
                search.put("default_field", "*");
                search.put("query", "*" + query.getValue() + "*");
                terms.put("query_string", search);
                listTerms.add(terms);
            }else {
                field.put("default_field", query.getField());
                field.put("query", "*" + query.getValue() + "*");
                terms.put("query_string", field);
                listTerms.add(terms);
            }
        }
        return listTerms;
    }

    public Object processMappingGetSales(JsonNode queryResult) throws Exception{
        Map<String, Object> finalResult = new LinkedHashMap<>();
        List<JsonNode> objList = new ArrayList<JsonNode>();
        for (JsonNode object : queryResult.get("hits")) {
            objList.add(object.get("_source"));
        }
        finalResult.put("total_sales_list", objList.size());
        finalResult.put("total_sales_data", queryResult.get("total").get("value").asInt());
        finalResult.put("Sales_list", objList);

        return finalResult;
    }

    public Object getTotalSalesAmount() throws Exception{
        String body = "{\"size\":0,\"aggs\":{\"total_sales\":{\"sum\":{\"field\":\"sales_amount\"}}}}";
        JsonNode queryResult = request.getWithBody("http://192.168.20.90:9200/sales_v2/_search", body).get("aggregations");

        if (queryResult != null) {
            return processMappingTotalSales(queryResult);
        } else {
            return null;
        }
    }

    public Object processMappingTotalSales(JsonNode queryResult) throws Exception {
        Map<String, Object> finalResult = new LinkedHashMap<>();
        finalResult.put("total_sales", queryResult.get("total_sales").get("value"));
        if (finalResult != null) {
            return finalResult;
        } else {
            return "Data not found";
        }
    }

    public Object getTotalSalesByRegion() throws Exception{
        String body = "{\"size\":0,\"aggs\":{\"sales_by_region\":{\"terms\":{\"field\":\"region\"},\"aggs\":{\"total_sales_per_region\":{\"sum\":{\"field\":\"sales_amount\"}}}}}}";
        JsonNode queryResult = request.getWithBody("http://192.168.20.90:9200/sales_v2/_search", body);

        JsonNode buckets = queryResult.get("aggregations").get("sales_by_region").get("buckets");
        if (queryResult != null) {
            if(buckets != null){
                return processMappingSalesByRegion(buckets);
            }
            return "Data Not Found";
        } else {
            return null;
        }
    }

    public Object processMappingSalesByRegion(JsonNode buckets) throws Exception{
        List<Map<String, Object>> finalResult = new ArrayList<>();
        for(JsonNode bucket : buckets){
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("region", bucket.get("key"));
            resultMap.put("total_data", bucket.get("doc_count"));
            resultMap.put("total_sales", bucket.get("total_sales_per_region").get("value"));
            finalResult.add(resultMap);
        }
        return finalResult;
    }

    public Object getSalesChanges() throws Exception{
        String body = "{\"size\":0,\"aggs\":{\"sales_per_day\":{\"date_histogram\":{\"field\":\"timestamp\",\"calendar_interval\":\"day\"},\"aggs\":{\"total_sales_per_day\":{\"sum\":{\"field\":\"sales_amount\"}},\"sales_diff\":{\"derivative\":{\"buckets_path\":\"total_sales_per_day\"}}}}}}";
        JsonNode queryResult = request.getWithBody("http://192.168.20.90:9200/sales_v2/_search", body);

        JsonNode buckets = queryResult.get("aggregations").get("sales_per_day").get("buckets");
        if (queryResult != null) {
            if(buckets != null){
               return processMappingSalesChanges(buckets);
            }
            return "Data Not Found";
        } else {
            return null;
        }
    }

    public Object processMappingSalesChanges(JsonNode buckets) throws Exception{
        List<Map<String, Object>> finalResult = new ArrayList<>();
        for(JsonNode bucket : buckets){
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("timestamp", bucket.get("key_as_string"));
            resultMap.put("total_data", bucket.get("doc_count"));
            resultMap.put("total_sales_per_day", bucket.get("total_sales_per_day").get("value"));

            Object setPendapatan = bucket.get("sales_diff") != null ? resultMap.put("pendapatan", bucket.get("sales_diff").get("value")) : resultMap.put("pendapatan", null) ;
            finalResult.add(resultMap);
        }
        return finalResult;
    }

    public Object maxSalesPerDay() throws Exception{
        String body = "{\"size\":1,\"aggs\":{\"per_day\":{\"date_histogram\":{\"field\":\"timestamp\",\"interval\":\"day\"},\"aggs\":{\"region\":{\"top_hits\":{\"sort\":[{\"sales_amount\":{\"order\":\"desc\"}}],\"size\":1}}}}}}";
        JsonNode queryResult = request.getWithBody("http://192.168.20.90:9200/sales_v2/_search", body);

        JsonNode buckets = queryResult.get("aggregations").get("per_day").get("buckets");
        if (queryResult != null) {
            if(buckets != null){
                return processMappingMaxSales(buckets);
            }
            return "Data Not Found";
        } else {
            return null;
        }
    }

    public Object processMappingMaxSales(JsonNode buckets) throws Exception{
        List<Map<String, Object>> finalResult = new ArrayList<>();
        for (JsonNode bucket : buckets) {
            JsonNode hits = bucket.get("region").get("hits").get("hits");
            for (JsonNode hit : hits) {
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("product_name", hit.get("_source").get("product_name").asText());
                resultMap.put("sales_amount", hit.get("_source").get("sales_amount").asInt());
                resultMap.put("region", hit.get("_source").get("region").asText());
                resultMap.put("timestamp", hit.get("_source").get("timestamp").asText());
                finalResult.add(resultMap);
            }
        }
        return finalResult;
    }

    public Object findById(String paramsId) throws Exception{
        JsonNode sales = request.findById("http://192.168.20.90:9200/sales_v2/_doc/" + paramsId);

        if(sales != null) {
            if(sales.get("found").asBoolean()){
                return sales.get("_source");
            }
            return "Data Not Found";
        }

        return null;
    }

    public Object create(Sales paramsSales) throws Exception{
        String hasheId = hasheId(paramsSales);
        JsonNode checkSales = request.findById("http://192.168.20.90:9200/sales_v2/_doc/" + hasheId);

        if(checkSales != null){
           return processCreate(checkSales, paramsSales, hasheId);
        }
        return null;
    }

    public Object processCreate(JsonNode checkSales, Sales paramsSales, String hasheId) throws Exception{
        if(!checkSales.get("found").asBoolean() ){
            Map<String, Object> sales = new LinkedHashMap<>();

            sales.put("id", hasheId);
            sales.put("product_name", paramsSales.getProduct_name());
            sales.put("sales_amount", paramsSales.getSales_amount());
            sales.put("region", paramsSales.getRegion());

            Object setTimestamp = paramsSales.getTimestamp() == null ? sales.put("timestamp", formatDate()) : sales.put("timestamp", paramsSales.getTimestamp());

            JsonNode createSales = request.create("http://192.168.20.90:9200/sales_v2/_doc/" + hasheId, sales);
            JsonNode response = request.findById("http://192.168.20.90:9200/sales_v2/_doc/" + hasheId);
            return response.get("_source");
        }
        return "Sales already exist";
    }

    public String hasheId(Sales paramsSales){
        String dataToHash = paramsSales.getId() + paramsSales.getTimestamp();
        String hashedId = DigestUtils.md5Hex(dataToHash);
        return hashedId;
    }

    public String formatDate(){
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return dateTime.format(formatter);
    }

    public Object update(Sales paramsSales) throws Exception{
        JsonNode checkSales = request.findById("http://192.168.20.90:9200/customers/_doc/" + paramsSales.getId());
        if(checkSales != null){
            return processUpdate(checkSales, paramsSales);
        }
        return null;
    }

    public Object processUpdate(JsonNode checkSales, Sales paramsSales) throws Exception{
        if( checkSales.get("found").asBoolean()){
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            Map<String, Object> salesMap = new LinkedHashMap<>();

            Optional.ofNullable(paramsSales.getProduct_name()).ifPresent(product_name -> salesMap.put("product_name", product_name));
            Optional.ofNullable(paramsSales.getSales_amount()).ifPresent(sales_amount -> salesMap.put("sales_amount", sales_amount));
            Optional.ofNullable(paramsSales.getRegion()).ifPresent(region -> salesMap.put("region", region));
            Optional.ofNullable(paramsSales.getTimestamp()).ifPresent(timestamp -> salesMap.put("timestamp", timestamp));

            bodyMap.put("doc", salesMap);

            JsonNode updateCustomer = request.update("http://192.168.20.90:9200/sales_v2/_update/" + paramsSales.getId(), bodyMap);
            JsonNode getSales = request.findById("http://192.168.20.90:9200/customers/_doc/" + paramsSales.getId());
            return getSales.get("_source");
        }
        return "Data Not Found";
    }

    public Object delete(String paramsId) throws Exception{
        JsonNode sales = request.findById("http://192.168.20.90:9200/sales_v2/_doc/" + paramsId);
        if(sales != null ){
            if(sales.get("found").asBoolean()){
                JsonNode deleteSales = request.delete("http://192.168.20.90:9200/sales_v2/_doc/" + paramsId);
                return "Successfully";
            }
            return "Data Not Found";
        }
        return null;
    }
}
