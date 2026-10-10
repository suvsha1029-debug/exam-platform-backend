const fs = require('node:fs');
const path = require('node:path');
const csc = require('@countrystatecity/countries');

const postalAlpha2 = require('../node_modules/postal-codes-js/generated/postal-codes-alpha2.json');
const postalFormatsDir = path.join(__dirname, '..', 'node_modules', 'postal-codes-js', 'formats');

const outputPath = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'assets', 'geo', 'country-registry.js');

const phoneDigitOverrides = new Map([
  ['AU', 9],
  ['BD', 10],
  ['CA', 10],
  ['IN', 10],
  ['LK', 9],
  ['NP', 10],
  ['SG', 8],
  ['AE', 9],
  ['GB', 10],
  ['US', 10]
]);

const postalLabelOverrides = new Map([
  ['AU', 'Postcode'],
  ['CA', 'Postal code'],
  ['GB', 'Postcode'],
  ['IN', 'Pincode'],
  ['US', 'ZIP code']
]);

const postalPlaceholderOverrides = new Map([
  ['AU', '4-digit postcode'],
  ['CA', 'e.g. K1A 0B1'],
  ['GB', 'e.g. SW1A 1AA'],
  ['IN', '6-digit PIN code'],
  ['US', '5-digit ZIP or ZIP+4']
]);

const stateOverrides = new Map([
  ['IN', [
    'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana',
    'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur',
    'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana',
    'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh',
    'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry'
  ]]
]);

function normalizeStateNames(states) {
  return [...new Set((states || []).map((state) => String(state || '').trim()).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right));
}

function buildPostalMeta(country) {
  const formatIndex = postalAlpha2[country.iso2];
  const format = readPostalFormat(formatIndex?.postalCodeFormat);
  const label = postalLabelOverrides.get(country.iso2) || 'Postal code';
  const placeholder = postalPlaceholderOverrides.get(country.iso2)
    || format?.Description
    || 'Postal / ZIP code';
  const regexSource = format?.ValidationRegex || '';
  const flags = regexSource && /[A-Za-z]/.test(regexSource) ? 'i' : '';
  const maxLength = deriveMaxLength(format);
  return {
    postalLabel: label,
    postalPlaceholder: placeholder,
    postalMaxLength: maxLength,
    postalPatternSource: regexSource,
    postalPatternFlags: flags
  };
}

function readPostalFormat(fileName) {
  if (!fileName) {
    return null;
  }
  const formatPath = path.join(postalFormatsDir, fileName);
  if (!fs.existsSync(formatPath)) {
    return null;
  }
  return JSON.parse(fs.readFileSync(formatPath, 'utf8'));
}

function deriveMaxLength(format) {
  if (!format?.TestData?.Valid?.length) {
    return 12;
  }
  return Math.max(
    4,
    Math.min(
      16,
      ...format.TestData.Valid.map((sample) => String(sample || '').length)
    )
  );
}

async function main() {
  const countries = await csc.getCountries();
  const registry = [];
  for (const country of countries) {
    const rawStates = await csc.getStatesOfCountry(country.iso2);
    const states = stateOverrides.get(country.iso2)
      || normalizeStateNames(rawStates.map((state) => state.name || state.native));
    registry.push({
      country: country.name,
      iso2: country.iso2,
      iso3: country.iso3,
      code: '+' + String(country.phonecode || '').trim(),
      phoneDigits: phoneDigitOverrides.get(country.iso2) || null,
      ...buildPostalMeta(country),
      states
    });
  }
  registry.sort((left, right) => {
    if (left.country === 'India') return -1;
    if (right.country === 'India') return 1;
    return left.country.localeCompare(right.country);
  });
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const body = [
    'window.CANDIDATE_LOCATION_REGISTRY = Object.freeze(',
    JSON.stringify(registry, null, 2),
    ');',
    ''
  ].join('\n');
  fs.writeFileSync(outputPath, body, 'utf8');
  console.log(`Wrote ${registry.length} countries to ${outputPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
