 private static void sharePointDispatch() throws IOException, URISyntaxException, JDOMException {
        URI outPath = new URI("VALid URI of Sharepoint");
        String BaseUrl = outPath.getScheme()+"://"+outPath.getHost();

        CookieStore httpCookieStore = new BasicCookieStore();
        HttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(httpCookieStore).disableRedirectHandling().build();

        HttpPost request = new HttpPost("https://login.microsoftonline.com/extSTS.srf");
        request.addHeader("Content-Type","application/xml");
        String body = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "      xmlns:a=\"http://www.w3.org/2005/08/addressing\"\n" +
                "      xmlns:u=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n" +
                "  <s:Header>\n" +
                "    <a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue</a:Action>\n" +
                "    <a:ReplyTo>\n" +
                "      <a:Address>http://www.w3.org/2005/08/addressing/anonymous</a:Address>\n" +
                "    </a:ReplyTo>\n" +
                "    <a:To s:mustUnderstand=\"1\">https://login.microsoftonline.com/extSTS.srf</a:To>\n" +
                "    <o:Security s:mustUnderstand=\"1\"\n" +
                "       xmlns:o=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n" +
                "      <o:UsernameToken>\n" +
                "        <o:Username>"+job.getOutputDirectoryAccessUserName()+"</o:Username>\n" +
                "        <o:Password>"+job.getOutputDirectoryAccessPassword()+"</o:Password>\n" +
                "      </o:UsernameToken>\n" +
                "    </o:Security>\n" +
                "  </s:Header>\n" +
                "  <s:Body>\n" +
                "    <t:RequestSecurityToken xmlns:t=\"http://schemas.xmlsoap.org/ws/2005/02/trust\">\n" +
                "      <wsp:AppliesTo xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n" +
                "        <a:EndpointReference>\n" +
                "          <a:Address>"+outPath.getHost()+"</a:Address>\n" +
                "        </a:EndpointReference>\n" +
                "      </wsp:AppliesTo>\n" +
                "      <t:KeyType>http://schemas.xmlsoap.org/ws/2005/05/identity/NoProofKey</t:KeyType>\n" +
                "      <t:RequestType>http://schemas.xmlsoap.org/ws/2005/02/trust/Issue</t:RequestType>\n" +
                "      <t:TokenType>urn:oasis:names:tc:SAML:1.0:assertion</t:TokenType>\n" +
                "    </t:RequestSecurityToken>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>\n";
        HttpEntity httpEntity = EntityBuilder.create().setContentType(ContentType.APPLICATION_XML).setText(body).build();
        request.setEntity(httpEntity);
        CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(request);
        SAXBuilder saxBuilder  = new SAXBuilder();
        org.jdom2.Document document = saxBuilder.build(new StringReader(EntityUtils.toString(response.getEntity())));
        String securityToken = document.getRootElement().getChildren().get(1).getChildren().get(0).getChildren().get(3).getChildren().get(0).getText();
        response.close();

        request = new HttpPost(BaseUrl+"/_forms/default.aspx?wa=wsignin1.0");
        HttpEntity httpEntity1 = EntityBuilder.create().setContentType(ContentType.TEXT_PLAIN).setText(securityToken).build();
        request.setEntity(httpEntity1);
        response = (CloseableHttpResponse) httpClient.execute(request);
        response.close();

        request = new HttpPost(BaseUrl+"/_api/contextinfo");
        response = (CloseableHttpResponse) httpClient.execute(request);
        document = saxBuilder.build(new StringReader(EntityUtils.toString(response.getEntity())));
        String formDigest = document.getRootElement().getChildren().get(1).getValue();
        System.out.println(response.getStatusLine());
        response.close();

        InputStream in = new URL("public url to sharepoint folder").openStream();
        File file = new File("file"+"."+"txt"));
        file.createNewFile();
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        IOUtils.copy(in, fileOutputStream);
        HttpPost httpPost = new HttpPost(outPath);
        httpPost.setHeader("X-RequestDigest",formDigest);
        httpPost.setHeader("Accept","application/json;odata=verbose");
        httpPost.setEntity(new FileEntity(file));
        response = (CloseableHttpResponse) httpClient.execute(httpPost);
        if(response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY){
            for (Header header:response.getAllHeaders()
            ) {
                if(header.getName().equals("Location")) {
                    URI uri1 = new URI(header.getValue());
                    String query = uri1.getRawQuery().split("&")[0].split("=")[1];
                    URI uri2 = new URI(BaseUrl+"/_api/web/GetFolderByServerRelativePath(decodedurl='"+query+"')/files/add(url='"+file.getPath()+"',overwrite=true)");
                    httpPost.setURI(uri2);
                    break;
                }
            }
            response = (CloseableHttpResponse)httpClient.execute(httpPost);
        }
        FileUtils.forceDelete(file);
        if(response.getStatusLine().getStatusCode()==HttpStatus.SC_OK) {
            logger.info("uploaded file to sharepoint");
        }
    }
