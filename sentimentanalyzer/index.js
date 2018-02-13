const elasticsearch = require('elasticsearch');
const ml = require('ml-sentiment')({lang: 'de'});

const client = new elasticsearch.Client({
  host: 'localhost:9200',
  apiVersion: '5.6',
  log: 'error'
});

let scores = [];

client.search({
  index: 'bookscan',
  type: 'blogarticle',
  size: 10000,
  scroll: '12000s'
}, function callback(error, response) {
  response.hits.hits.forEach(hit => {
    scores.push(ml.classify(hit._source.content))
  });

  console.info("processed: " + scores.length);

  if (response.hits.total > scores.length) {
    client.scroll({
      scrollId: response._scroll_id,
      scroll: '12000s'
    }, callback);
  } else {
    console.info("average score: " + scores.reduce((prev, current) => prev + current, 0) / scores.length);
  }
});