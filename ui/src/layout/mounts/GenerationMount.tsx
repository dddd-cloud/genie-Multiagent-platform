import { useNavigate } from 'react-router-dom';
import GenerationPage from '@/features/generation/GenerationPage';
import type { GenerationDraftResponse } from '@/services/generation';
import {
  generationDraftTarget,
  mapDraftToAgentForm,
  mapDraftToTeamForm,
} from './draftMapping';

export default function GenerationMount() {
  const navigate = useNavigate();

  function handleDraftReady(result: GenerationDraftResponse) {
    if (generationDraftTarget(result) === 'TEAM') {
      navigate('/app/teams/new', {
        state: { draft: mapDraftToTeamForm(result.draft) },
      });
      return;
    }
    navigate('/app/settings/agents/new', {
      state: { draft: mapDraftToAgentForm(result.draft) },
    });
  }

  return <GenerationPage onDraftReady={handleDraftReady} />;
}
