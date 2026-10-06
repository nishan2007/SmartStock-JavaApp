import test from 'node:test';
import assert from 'node:assert/strict';
import {questionsFor,descriptionWithAnswers,repeatDraft} from '../src/quoteGuide.mjs';

test('inspired embroidery requests ask for garment sizes and placement',()=>{
  const questions=questionsFor('Company polo',{category:'Apparel',productionMethod:'Embroidery',tags:['corporate']});
  assert.deepEqual(questions.map(q=>q.key),['garment','sizes','placement']);
  assert.equal(descriptionWithAnswers('Like the example, in blue.',{garment:'Polo',sizes:'5 medium',placement:'Left chest'},questions),
    'Like the example, in blue.\n\nProject preferences:\nGarment or item: Polo\nSizes and quantities: 5 medium\nEmbroidery placement: Left chest');
});
test('3D and printed cards get relevant questions without adding empty answers',()=>{
  assert.deepEqual(questionsFor('3D Printing',null).map(q=>q.key),['text','scale','use']);
  const cards=questionsFor('Business Cards',null);
  assert.deepEqual(cards.map(q=>q.key),['sides','finish','artwork']);
  assert.equal(descriptionWithAnswers('Please quote this.',{},cards),'Please quote this.');
});
test('repeat request restores guided answers and print preference without duplicating them',()=>{
  const questions=questionsFor('3D Printing',null);
  const previous='A desk sign.\n\nProject preferences:\nNames or text to include: Deckers\nMeasurement units and intended scale: 150 mm wide\nModel units: millimeters\nPrint quality preference: Prioritize fine detail';
  const draft=repeatDraft(previous,questions);
  assert.equal(draft.description,'A desk sign.');
  assert.deepEqual(draft.answers,{text:'Deckers',scale:'150 mm wide'});
  assert.equal(draft.modelUnits,'MM');
  assert.equal(descriptionWithAnswers(draft.description,draft.answers,questions),'A desk sign.\n\nProject preferences:\nNames or text to include: Deckers\nMeasurement units and intended scale: 150 mm wide');
  assert.deepEqual(repeatDraft('A different request.\n\nProject preferences:\nUnknown: keep this',questions),{description:'A different request.\n\nProject preferences:\nUnknown: keep this',answers:{},modelUnits:''});
});
