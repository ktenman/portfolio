UPDATE instrument
SET provider_name = 'LIGHTYEAR'
WHERE symbol IN (
    'VUAA:GER:EUR',
    'QDVE:GER:EUR',
    'WTAI:MIL:EUR',
    'SPYL:GER:EUR',
    'XAIX:GER:EUR',
    'CSX5:AEX:EUR',
    'VNRT:AEX:EUR',
    'WBIT:GER:EUR'
);
