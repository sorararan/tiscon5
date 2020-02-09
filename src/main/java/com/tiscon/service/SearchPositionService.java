package com.tiscon.service;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;

@Service
public class SearchPositionService {
    private static final String BASE_URL = "https://map.yahooapis.jp/geocode/V1/geoCoder";

    // 0: 緯度, 1: 経度
    public Double[] search(String address) throws Exception {
        String url = BASE_URL + "?" + "appid=" + System.getenv("YOLP_APP_ID") + "&query=" + address + "&results=1";
        return xml2array(get(url));
    }

    private Document get(String url) throws Exception {
        URL urlObj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
        con.setRequestMethod("GET");
        con.connect();

        InputStream is = con.getInputStream();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        is.close();

        return doc;
    }

    private Double[] xml2array(Document doc) throws XPathExpressionException {
        Double[] result = new Double[2];
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList features = (NodeList) xpath.evaluate("YDF/Feature", doc, XPathConstants.NODESET);
        Node feature = features.item(0);
        String type = xpath.evaluate("Geometry/Type", feature);
        if (type.equals("point")) {
            String coordinates = xpath.evaluate("Geometry/Coordinates", feature);
            String[] ll = coordinates.split(",");
            // 緯度
            result[0] = Double.parseDouble(ll[1]);
            // 経度
            result[1] = Double.parseDouble(ll[0]);
        }
        return result;
    }
}
