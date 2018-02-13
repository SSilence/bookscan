const fs = require('fs');

const sentiwsNegative = fs.readFileSync('./SentiWS_v1.8c/SentiWS_v1.8c_Negative.txt');
const sentiwsPositive = fs.readFileSync('./SentiWS_v1.8c/SentiWS_v1.8c_Positive.txt');
const sentiws = sentiwsNegative + '\n' + sentiwsPositive;
const wordlist = JSON.parse(fs.readFileSync('./node_modules/ml-sentiment/german.json'));

for(line of sentiws.split('\n')) {
    const e = line.split(/\||\t/g);
    const words = [ e[0] ];
    if (e.length > 3) {
        words.concat(e[3].split(","));
    }
    
    for (word of words) {
        wordlist[word.toLowerCase()] = parseFloat(e[2]);
    }
}

fs.writeFileSync('./german.json', JSON.stringify(wordlist, null, 4), 'utf8');