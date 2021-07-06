DataImport Handler 적용 방법

MYSQL Connection 설정

– Solrconfig.xml 파일에서 db 설정 파일을 지정한다.
– Solrconfig.xml에서 데이터를 정의한 xml을 지정한다.
```
<requestHandler name="/dataimport“ class="org.apache.solr.handler.dataimport.DataImportHandler">
<lst name="defaults">
<str name="config">db-data-config.xml</str>
</lst>
</requestHandler> 
```

– db-data-config.xml 파일에서 데이터에 대한 SQL문을 적용한다.
```
    <dataConfig>
    <dataSource type="JdbcDataSource" driver="com.mysql.jdbc.Driver”
    url="jdbc:mysql://localhost/solr" user="solr" password="solr" name="solr"/>
    <document>
    <entity name="product" query="select id, mid, name from product">
    <field column="id" name="pid" />
    <field column="mid" name="mid" />
    <field column="name" name="pname" />
    <field column=“hname" name=“hname" />
    <entity name="maker" query="select mid, name from maker where mid = '${product.mid}'">
    <field column="mid" name="mid" />
    <field column="name" name="mname" />
    </entity>
    </entity>
    </document>
    </dataConfig>
```

색인 설정
– Shema.xml 파일에서 검색 필드를 설정
```
<field name="pid" type="string" indexed="true" stored="true" required="true" />
<field name="mid" type="int" indexed="true" stored="true" multiValued="false“ />
<field name="pname" type="text" indexed="true" stored="true" multiValued="true“ />
<field name="mname" type="text" indexed="true" stored="true" multiValued="true“ />
……..
<defaultSearchField>pname</defaultSearchField>
<defaultSearchField>mname</defaultSearchField>
……..
<uniqueKey>pid</uniqueKey>
……..
<copyField source="pname" dest="text"/>
<copyField source="mname" dest="text"/>
```
– Solr 실행
```
java -Dsolr.solr.home="./example-DIH/solr/" -jar start.jar
```
– 색인 실행
```
http://localhost:8983/solr/db/dataimport?command=full-import
```


Delta Import  적용 방법
MYSQL Connection 설정
– db-data-config.xml 파일에서 데이터에 대한 SQL문을 적용한다.
```
<dataConfig>
<dataSource type="JdbcDataSource" driver="com.mysql.jdbc.Driver"
url="jdbc:mysql://localhost/solr" user="solr" password="solr" name="solr"/>
<document>
<entity name="product" pk="id“
    query="select * from product“
    deltaImportQuery="select * from product where id='${dataimporter.delta.id}'“
    deltaQuery="select id from product where lastmodified > '${dataimporter.last_index_time}'">
    <field column="id" name="pid" />
    <field column="mid" name="mid" />
    <field column="name" name="pname" />
        <entity name="maker" pk="mid“
               query="select mid from maker where mid='${product.mid}'">
            <field column="mid" name="mid" />
            <field column="name" name="mname" />
        </entity>
</entity>
</document>
</dataConfig>
```
– 색인 실행
```
http://localhost:8983/solr/db/dataimport?command=delta-import
```

Query Examples
```
• mission이나 impossible이 포함되고 releaseDate로 내림차순 검색
– q=mission impossible; releaseDate desc
• mission을 포함하고actor에 cruise가 포함되지 않은 문서를 검색
– q=+mission –actor:cruise
• mission impossible이 붙고, actor에 cruise가 포함되지 않은 문서 검색
– q=“mission impossible” –actor:cruise
• title에 spiderman을 description의 spiderman보다 10의 가중치 부여
– q=title:spiderman^10 description:spiderman
• description필드에서 spiderman과 movie가 10단어 이내의 문서 검색
– q=description:“spiderman movie”~10
• HDTV를 반드시 포함하고 weight이 40 이상인 문서를 검색
– q=+HDTV +weight:[40 TO *]
• Wildcard queries
• q=te?t
• q=te*t
• q=test*
```

Autocomplete
– Solrconfig.xml에 suggest 기능을 추가한다.
https://solr.apache.org/guide/8_9/spell-checking.html
```


<searchComponent name="suggest" class="solr.SpellCheckComponent">
<lst name="spellchecker">
<str name="name">suggest</str>
<str name="classname">org.apache.solr.spelling.suggest.Suggester</str>
<str name="lookupImpl">org.apache.solr.spelling.suggest.tst.TSTLookup</str>
<str name="field">name_autocomplete</str>
</lst>
</searchComponent>
<requestHandler name="/suggest" class="org.apache.solr.handler.component.SearchHandler">
<lst name="defaults">
<str name="spellcheck">true</str>
<str name="spellcheck.dictionary">suggest</str>
<str name="spellcheck.count">10</str>
</lst>
<arr name="components">
<str>suggest</str>
</arr>
</requestHandler>
```

– Shema.xml에 suggest 필드를 추가한다.
```
• 검색 실행 (http://localhost:8983/solr/db/suggest?spellcheck.build=true)
<fieldType name="text_auto" class="solr.TextField" positionIncrementGap="100">
<analyzer>
<tokenizer class="solr.WhitespaceTokenizerFactory"/>
<filter class="solr.WordDelimiterFilterFactory" generateWordParts="1” generateNumberParts="1"
catenateWords="1" catenateNumbers="1" catenateAll="0” splitOnCaseChange="1"/>
<filter class="solr.LowerCaseFilterFactory"/>
</analyzer>
</fieldType>
<field name="name_autocomplete" type="text_auto" indexed="true" stored="true” multiValued="false" />
<copyField source="name" dest="name_autocomplete" />
```

Basic Dictionary
- 동의어/불용어 사전
동의어 사전
• 항목 (synonyms.txt)
불용어
• 항목 (stopwords.txt)

highlighting
```
https://solr.apache.org/guide/8_9/highlighting.html
```